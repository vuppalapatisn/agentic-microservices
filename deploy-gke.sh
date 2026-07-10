#!/usr/bin/env bash
#
# Deploy the full agentic-microservices stack to a Google Kubernetes Engine
# (GKE) *Standard* cluster. This is the GKE counterpart of deploy-ibm-cloud.sh.
#
# Why GKE Standard (not Autopilot): promtail is a DaemonSet that mounts hostPath
# volumes (/var/log/pods, /run/promtail). Autopilot rejects hostPath, which would
# break log shipping to Loki. Standard clusters allow it.
#
# What differs from the IBM Cloud path:
#   * The bundled ingress (k8s/ingress, k8s/observability/ingress.yaml) uses AWS
#     ALB annotations that do nothing on GKE. This script skips them and installs
#     ingress-nginx instead, then applies equivalent path-based rules.
#   * Images: by default this reuses the public Docker Hub images already named in
#     the manifests (no build). Set IMAGE_MODE=artifact-registry to build every
#     service and push to Google Artifact Registry in your project.
#
# Prerequisites (one-time, not automated here):
#   1. gcloud CLI installed and authenticated (gcloud auth login). Google Cloud
#      Shell has gcloud + kubectl + helm preinstalled and pre-authenticated.
#   2. kubectl and helm on PATH (gcloud components install kubectl; https://helm.sh).
#   3. For IMAGE_MODE=artifact-registry only: docker + Maven, and
#      `gcloud auth configure-docker $AR_LOCATION-docker.pkg.dev`.
#
# Required:
#   PROJECT_ID    your GCP project id (no default)
# Optional overrides (defaults shown):
#   ZONE=us-central1-a  CLUSTER_NAME=agentic-microservices
#   MACHINE_TYPE=e2-standard-4  NUM_NODES=1
#   IMAGE_MODE=dockerhub | artifact-registry
#   DOCKERHUB_NAMESPACE=sudhavuppalapati        (dockerhub mode)
#   AR_LOCATION=us-central1  AR_REPO=agentic     (artifact-registry mode)
#   OPENAI_API_KEY=...   (if set, the agent secret is created/updated)
#
# Example:
#   PROJECT_ID=my-proj OPENAI_API_KEY=sk-... ./deploy-gke.sh
#   PROJECT_ID=my-proj IMAGE_MODE=artifact-registry ./deploy-gke.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_TAG="$(date +%Y%m%d%H%M%S)"

PROJECT_ID="${PROJECT_ID:-}"
ZONE="${ZONE:-us-central1-a}"
CLUSTER_NAME="${CLUSTER_NAME:-agentic-microservices}"
MACHINE_TYPE="${MACHINE_TYPE:-e2-standard-4}"
NUM_NODES="${NUM_NODES:-1}"
IMAGE_MODE="${IMAGE_MODE:-dockerhub}"
DOCKERHUB_NAMESPACE="${DOCKERHUB_NAMESPACE:-sudhavuppalapati}"
AR_LOCATION="${AR_LOCATION:-us-central1}"
AR_REPO="${AR_REPO:-agentic}"
OPENAI_API_KEY="${OPENAI_API_KEY:-}"

fail() {
  echo
  echo "GKE deployment failed. Check the command output above."
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command '$1' not found on PATH."
    echo "Tip: Google Cloud Shell has gcloud, kubectl, and helm preinstalled."
    fail
  }
}

if [ -z "$PROJECT_ID" ]; then
  echo "PROJECT_ID is required, e.g.: PROJECT_ID=my-proj ./deploy-gke.sh"
  exit 1
fi

echo "[1/11] Checking prerequisites (gcloud, kubectl, helm)..."
require_cmd gcloud
require_cmd kubectl
require_cmd helm
if [ "$IMAGE_MODE" = "artifact-registry" ]; then
  require_cmd docker
  require_cmd mvn
fi

echo "[2/11] Setting project ($PROJECT_ID) and enabling GKE API..."
gcloud config set project "$PROJECT_ID" || fail
gcloud services enable container.googleapis.com || fail
if [ "$IMAGE_MODE" = "artifact-registry" ]; then
  gcloud services enable artifactregistry.googleapis.com || fail
fi

echo "[3/11] Ensuring Standard cluster '$CLUSTER_NAME' exists in $ZONE..."
if gcloud container clusters describe "$CLUSTER_NAME" --zone "$ZONE" >/dev/null 2>&1; then
  echo "Cluster already exists; reusing it."
else
  echo "Creating cluster ($NUM_NODES x $MACHINE_TYPE, zonal)..."
  gcloud container clusters create "$CLUSTER_NAME" \
    --zone "$ZONE" \
    --num-nodes "$NUM_NODES" \
    --machine-type "$MACHINE_TYPE" \
    --release-channel regular || fail
fi

echo "[4/11] Pointing kubectl at the cluster..."
gcloud container clusters get-credentials "$CLUSTER_NAME" --zone "$ZONE" || fail

echo "[5/11] Applying namespaces..."
kubectl apply -f "$ROOT_DIR/k8s/namespace.yaml" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/namespace.yaml" || fail

# ---------------------------------------------------------------------------
# Optional: build every service and push to Google Artifact Registry.
# ---------------------------------------------------------------------------
build_and_push_ar() {
  local name="$1" dir="$2" needs_mvn="$3"
  local image="$AR_LOCATION-docker.pkg.dev/$PROJECT_ID/$AR_REPO/$name"
  cd "$ROOT_DIR/microservices/$dir" || fail
  if [ "$needs_mvn" = "yes" ]; then
    mvn clean package || fail
  fi
  echo "[build] $image:$IMAGE_TAG..."
  docker build -t "$image:$IMAGE_TAG" . || fail
  echo "[push]  $image:$IMAGE_TAG..."
  docker push "$image:$IMAGE_TAG" || fail
  cd "$ROOT_DIR" || fail
}

if [ "$IMAGE_MODE" = "artifact-registry" ]; then
  echo "[6/11] Building + pushing images to Artifact Registry ($AR_LOCATION/$AR_REPO)..."
  if ! gcloud artifacts repositories describe "$AR_REPO" --location "$AR_LOCATION" >/dev/null 2>&1; then
    gcloud artifacts repositories create "$AR_REPO" \
      --repository-format docker --location "$AR_LOCATION" || fail
  fi
  gcloud auth configure-docker "$AR_LOCATION-docker.pkg.dev" --quiet || fail
  build_and_push_ar "observability-server" "observability-server" "yes"
  build_and_push_ar "observability-debug-agent-java" "observability-debug-agent" "no"
  build_and_push_ar "product" "product" "yes"
  build_and_push_ar "images" "images" "yes"
  build_and_push_ar "ecommerce" "ecommerce" "yes"
else
  echo "[6/11] IMAGE_MODE=dockerhub: reusing public docker.io/$DOCKERHUB_NAMESPACE/* images (no build)."
fi

echo "[7/11] Applying app + observability manifests (skipping ALB ingress)..."
kubectl apply -f "$ROOT_DIR/k8s/product" || fail
kubectl apply -f "$ROOT_DIR/k8s/images" || fail
kubectl apply -f "$ROOT_DIR/k8s/ecommerce" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/prometheus" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/loki" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/promtail" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/grafana" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability-server" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability-debug-agent/configmap.yaml" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability-debug-agent/deployment.yaml" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability-debug-agent/service.yaml" || fail

if [ "$IMAGE_MODE" = "artifact-registry" ]; then
  echo "        Repointing deployments to Artifact Registry images ($IMAGE_TAG)..."
  AR="$AR_LOCATION-docker.pkg.dev/$PROJECT_ID/$AR_REPO"
  kubectl set image deployment/product product=$AR/product:$IMAGE_TAG -n ecommerce || fail
  kubectl set image deployment/images images=$AR/images:$IMAGE_TAG -n ecommerce || fail
  kubectl set image deployment/ecommerce ecommerce=$AR/ecommerce:$IMAGE_TAG -n ecommerce || fail
  kubectl set image deployment/observability-server observability-server=$AR/observability-server:$IMAGE_TAG -n observability || fail
  kubectl set image deployment/observability-debug-agent observability-debug-agent=$AR/observability-debug-agent-java:$IMAGE_TAG -n observability || fail
else
  if ! kubectl get secret dockerhub-registry-secret -n ecommerce >/dev/null 2>&1; then
    echo
    echo "NOTE: 'dockerhub-registry-secret' is absent. Public Docker Hub images pull"
    echo "      fine without it; if any pod shows ImagePullBackOff the repos are"
    echo "      private — create the pull secret (see k8s/dockerhub-secret.yaml)."
    echo
  fi
fi

echo "[8/11] Configuring the OpenAI secret for the agent..."
if [ -n "$OPENAI_API_KEY" ]; then
  kubectl create secret generic observability-debug-agent-secret \
    --from-literal=OPENAI_API_KEY="$OPENAI_API_KEY" \
    -n observability --dry-run=client -o yaml | kubectl apply -f - || fail
  kubectl rollout restart deployment/observability-debug-agent -n observability || fail
elif kubectl get secret observability-debug-agent-secret -n observability >/dev/null 2>&1; then
  echo "        Secret already exists; leaving it as-is."
else
  echo
  echo "WARNING: OPENAI_API_KEY not provided and no existing secret found."
  echo "         Pods will start, but /api/v1/investigate fails until you run:"
  echo "         kubectl create secret generic observability-debug-agent-secret \\"
  echo "           --from-literal=OPENAI_API_KEY=your-key -n observability"
  echo
fi

echo "[9/11] Installing ingress-nginx (if not already present)..."
if ! helm status ingress-nginx -n ingress-nginx >/dev/null 2>&1; then
  helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx || fail
  helm repo update || fail
  helm install ingress-nginx ingress-nginx/ingress-nginx \
    --namespace ingress-nginx --create-namespace || fail
else
  echo "        ingress-nginx already installed; reusing it."
fi

echo "        Applying GKE-compatible ingress rules..."
kubectl apply -f - <<'EOF' || fail
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ecommerce-ingress
  namespace: ecommerce
spec:
  ingressClassName: nginx
  rules:
    - http:
        paths:
          - path: /ecommerceApp
            pathType: Prefix
            backend: { service: { name: ecommerce-service, port: { number: 8090 } } }
          - path: /product-service
            pathType: Prefix
            backend: { service: { name: product-service, port: { number: 8090 } } }
          - path: /image-service
            pathType: Prefix
            backend: { service: { name: images-service, port: { number: 8090 } } }
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: observability-ingress
  namespace: observability
spec:
  ingressClassName: nginx
  rules:
    - http:
        paths:
          - path: /observability
            pathType: Prefix
            backend: { service: { name: observability-debug-agent, port: { number: 8092 } } }
EOF

echo "[10/11] Waiting for rollouts..."
kubectl rollout status deployment/product -n ecommerce || fail
kubectl rollout status deployment/images -n ecommerce || fail
kubectl rollout status deployment/ecommerce -n ecommerce || fail
kubectl rollout status deployment/prometheus -n observability || fail
kubectl rollout status deployment/loki -n observability || fail
kubectl rollout status deployment/grafana -n observability || fail
kubectl rollout status deployment/observability-server -n observability || fail
kubectl rollout status deployment/observability-debug-agent -n observability || fail

echo "[11/11] Resolving the ingress external IP..."
EXTERNAL_IP=""
for _ in $(seq 1 30); do
  EXTERNAL_IP="$(kubectl get svc ingress-nginx-controller -n ingress-nginx \
    -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null)"
  [ -n "$EXTERNAL_IP" ] && break
  echo "        waiting for load balancer IP..."
  sleep 10
done

echo
echo "Pods (ecommerce):";      kubectl get pods -n ecommerce
echo
echo "Pods (observability):";  kubectl get pods -n observability
echo
echo "Ingress:";              kubectl get ingress -A
echo
if [ -n "$EXTERNAL_IP" ]; then
  echo "GKE deployment complete. External IP: $EXTERNAL_IP"
  echo "  Ecommerce API : http://$EXTERNAL_IP/ecommerceApp/ecommerce-service/ecommerceProducts"
  echo "  Agent chat UI : http://$EXTERNAL_IP/observability"
else
  echo "GKE deployment complete, but the load balancer IP is not assigned yet."
  echo "Re-check with: kubectl get svc ingress-nginx-controller -n ingress-nginx"
fi
echo
echo "Tear down when finished (stops node + LB billing):"
echo "  gcloud container clusters delete $CLUSTER_NAME --zone $ZONE"
