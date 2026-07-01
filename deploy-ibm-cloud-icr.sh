#!/usr/bin/env bash
#
# Build, push, and deploy all services to an IBM Cloud Kubernetes Service
# (IKS) cluster using IBM Cloud Container Registry (icr.io) instead of
# Docker Hub (see deploy-ibm-cloud.sh for the Docker Hub version).
#
# Why ICR instead of Docker Hub: ICR sits on IBM's own network, so IKS
# worker nodes can reach it without needing outbound internet access (no
# VPC Public Gateway dependency, unlike pulling from docker.io). IKS
# clusters also get automatic image-pull access to ICR namespaces in the
# same IBM Cloud account, with no manual imagePullSecret required.
#
# Prerequisites:
#   1. ibmcloud CLI installed and logged in (ibmcloud login), with the
#      kubernetes-service and container-registry plugins installed:
#        ibmcloud plugin install kubernetes-service
#        ibmcloud plugin install container-registry
#   2. docker installed and running locally.
#
# Override defaults via environment variables, e.g.:
#   IKS_CLUSTER_ID=myothercluster ICR_NAMESPACE=myns ./deploy-ibm-cloud-icr.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_TAG="$(date +%Y%m%d%H%M%S)"
ICR_REGISTRY="${ICR_REGISTRY:-icr.io}"
ICR_NAMESPACE="${ICR_NAMESPACE:-agentic}"
IKS_CLUSTER_ID="${IKS_CLUSTER_ID:-d92fbhuh0h54olrrmsfg}"

fail() {
  echo
  echo "IBM Cloud (ICR) deployment failed. Check the command output above."
  exit 1
}

build_and_push() {
  local name="$1"
  local dir="$2"
  local needs_mvn="$3"
  local image="$ICR_REGISTRY/$ICR_NAMESPACE/$name"

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

echo "[1/10] Logging in to IBM Cloud Container Registry..."
ibmcloud cr login || fail

echo "[2/10] Ensuring ICR namespace '$ICR_NAMESPACE' exists..."
ibmcloud cr namespace-add "$ICR_NAMESPACE" || echo "Namespace '$ICR_NAMESPACE' may already exist, continuing."

echo "[3/10] Pointing kubectl at IBM Cloud Kubernetes cluster ($IKS_CLUSTER_ID)..."
ibmcloud ks cluster config --cluster "$IKS_CLUSTER_ID" || fail

echo "[4/10] Using image tag $IMAGE_TAG, pushing to $ICR_REGISTRY/$ICR_NAMESPACE/*..."

echo "[5/10] Building and pushing observability-server..."
build_and_push "observability-server" "observability-server" "yes"

echo "[6/10] Building and pushing observability-debug-agent (API + chat UI)..."
build_and_push "observability-debug-agent" "observability-debug-agent" "no"

echo "[7/10] Building and pushing product-service..."
build_and_push "product" "product" "yes"

echo "[8/10] Building and pushing images..."
build_and_push "images" "images" "yes"

echo "[9/10] Building and pushing ecommerce..."
build_and_push "ecommerce" "ecommerce" "yes"

echo "[10/10] Applying Kubernetes manifests and rolling out..."
cd "$ROOT_DIR" || fail
kubectl apply -f "$ROOT_DIR/k8s/namespace.yaml" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/namespace.yaml" || fail

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

kubectl set image deployment/product product=$ICR_REGISTRY/$ICR_NAMESPACE/product:$IMAGE_TAG -n ecommerce || fail
kubectl set image deployment/images images=$ICR_REGISTRY/$ICR_NAMESPACE/images:$IMAGE_TAG -n ecommerce || fail
kubectl set image deployment/ecommerce ecommerce=$ICR_REGISTRY/$ICR_NAMESPACE/ecommerce:$IMAGE_TAG -n ecommerce || fail
kubectl set image deployment/observability-server observability-server=$ICR_REGISTRY/$ICR_NAMESPACE/observability-server:$IMAGE_TAG -n observability || fail
kubectl set image deployment/observability-debug-agent observability-debug-agent=$ICR_REGISTRY/$ICR_NAMESPACE/observability-debug-agent:$IMAGE_TAG -n observability || fail

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
echo "IBM Cloud (ICR) deployment complete."
echo "Images: $ICR_REGISTRY/$ICR_NAMESPACE/<service>:$IMAGE_TAG"
