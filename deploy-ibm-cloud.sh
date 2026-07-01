#!/usr/bin/env bash
#
# Build, push, and deploy all services to an IBM Cloud Kubernetes Service
# (IKS) cluster. Unlike start.sh (which targets a local Docker Desktop
# cluster and never leaves the local image cache), this script pushes every
# image to Docker Hub so the remote IKS worker nodes can pull it.
#
# Prerequisites (one-time, not automated here):
#   1. ibmcloud CLI installed and logged in (ibmcloud login), with the
#      kubernetes-service plugin installed (ibmcloud plugin install kubernetes-service).
#   2. docker login to Docker Hub as the account that owns the
#      $DOCKERHUB_NAMESPACE repositories referenced in k8s/*/deployment.yaml.
#   3. The "dockerhub-registry-secret" image pull secret created in both the
#      ecommerce and observability namespaces — see k8s/dockerhub-secret.yaml
#      for the exact `kubectl create secret docker-registry` command.
#
# Override defaults via environment variables, e.g.:
#   IKS_CLUSTER_ID=myothercluster ./deploy-ibm-cloud.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_TAG="$(date +%Y%m%d%H%M%S)"
DOCKERHUB_NAMESPACE="${DOCKERHUB_NAMESPACE:-sudhavuppalapati}"
IKS_CLUSTER_ID="${IKS_CLUSTER_ID:-d92fbhuh0h54olrrmsfg}"

fail() {
  echo
  echo "IBM Cloud deployment failed. Check the command output above."
  exit 1
}

build_and_push() {
  local name="$1"
  local dir="$2"
  local needs_mvn="$3"
  local image="docker.io/$DOCKERHUB_NAMESPACE/$name"

  cd "$ROOT_DIR/microservices/$dir" || fail
  if [ "$needs_mvn" = "yes" ]; then
    mvn clean package || fail
  fi
  echo "[build] docker image $image:$IMAGE_TAG..."
  docker build --no-cache -t "$image:$IMAGE_TAG" . || fail
  echo "[push] $image:$IMAGE_TAG..."
  docker push "$image:$IMAGE_TAG" || fail
  cd "$ROOT_DIR" || fail
}

echo "[1/9] Pointing kubectl at IBM Cloud Kubernetes cluster ($IKS_CLUSTER_ID)..."
ibmcloud ks cluster config --cluster "$IKS_CLUSTER_ID" || fail

echo "[2/9] Using image tag $IMAGE_TAG, pushing to docker.io/$DOCKERHUB_NAMESPACE/*..."

echo "[3/9] Building and pushing observability-server..."
build_and_push "observability-server" "observability-server" "yes"

echo "[4/9] Building and pushing observability-debug-agent (API + chat UI)..."
build_and_push "observability-debug-agent" "observability-debug-agent" "no"

echo "[5/9] Building and pushing product-service..."
build_and_push "product" "product" "yes"

echo "[6/9] Building and pushing images..."
build_and_push "images" "images" "yes"

echo "[7/9] Building and pushing ecommerce..."
build_and_push "ecommerce" "ecommerce" "yes"

echo "[8/9] Applying Kubernetes manifests..."
cd "$ROOT_DIR" || fail
kubectl apply -f "$ROOT_DIR/k8s/namespace.yaml" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/namespace.yaml" || fail

for ns in ecommerce observability; do
  if ! kubectl get secret dockerhub-registry-secret -n "$ns" >/dev/null 2>&1; then
    echo
    echo "WARNING: secret 'dockerhub-registry-secret' not found in namespace '$ns'."
    echo "Pods will fail to pull images (ImagePullBackOff) until you create it."
    echo "See k8s/dockerhub-secret.yaml for the exact kubectl create secret command."
    echo
  fi
done

kubectl apply -f "$ROOT_DIR/k8s/product" || fail
kubectl apply -f "$ROOT_DIR/k8s/images" || fail
kubectl apply -f "$ROOT_DIR/k8s/ecommerce" || fail
kubectl apply -f "$ROOT_DIR/k8s/ingress" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/prometheus" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/loki" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/promtail" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/grafana" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability-server" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability-debug-agent/configmap.yaml" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability-debug-agent/deployment.yaml" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability-debug-agent/service.yaml" || fail

echo "[9/9] Rolling out image tag $IMAGE_TAG..."
kubectl set image deployment/product product=docker.io/$DOCKERHUB_NAMESPACE/product:$IMAGE_TAG -n ecommerce || fail
kubectl set image deployment/images images=docker.io/$DOCKERHUB_NAMESPACE/images:$IMAGE_TAG -n ecommerce || fail
kubectl set image deployment/ecommerce ecommerce=docker.io/$DOCKERHUB_NAMESPACE/ecommerce:$IMAGE_TAG -n ecommerce || fail
kubectl set image deployment/observability-server observability-server=docker.io/$DOCKERHUB_NAMESPACE/observability-server:$IMAGE_TAG -n observability || fail
kubectl set image deployment/observability-debug-agent observability-debug-agent=docker.io/$DOCKERHUB_NAMESPACE/observability-debug-agent:$IMAGE_TAG -n observability || fail

kubectl rollout status deployment/product -n ecommerce || fail
kubectl rollout status deployment/images -n ecommerce || fail
kubectl rollout status deployment/ecommerce -n ecommerce || fail
kubectl rollout status deployment/prometheus -n observability || fail
kubectl rollout status deployment/loki -n observability || fail
kubectl rollout status deployment/grafana -n observability || fail
kubectl rollout status deployment/observability-server -n observability || fail
kubectl rollout status deployment/observability-debug-agent -n observability || fail

echo
echo "Pods:"
kubectl get pods -n ecommerce
echo
echo "Observability Pods:"
kubectl get pods -n observability
echo
echo "Services:"
kubectl get svc -n ecommerce
echo
echo "Observability Services:"
kubectl get svc -n observability
echo
echo "Ingress:"
kubectl get ingress -n ecommerce
echo
echo "IBM Cloud deployment complete. Image tag: $IMAGE_TAG"
