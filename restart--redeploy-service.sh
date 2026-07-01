#!/usr/bin/env bash

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_TAG="$(date +%Y%m%d%H%M%S)"

usage() {
  cat <<'EOF'

restart--redeploy-service.sh - rebuild and redeploy selected services

Usage:
  ./restart--redeploy-service.sh <service> [service2 ...]
  ./restart--redeploy-service.sh --help

Custom-built services (mvn clean package + docker build + kubectl set image):
  observability-server
  observability-debug-agent
  ecommerce          (alias: ecommerce-service)
  product            (alias: product-service)
  images             (alias: images-service)

Observability stack (kubectl apply + rollout restart; upstream images):
  grafana            (alias: graphana)
  prometheus
  loki
  promtail

Other:
  ingress

Examples:
  ./restart--redeploy-service.sh observability-debug-agent
  ./restart--redeploy-service.sh observability-server observability-debug-agent
  ./restart--redeploy-service.sh grafana observability-server
  ./restart--redeploy-service.sh ecommerce product images

EOF
}

fail() {
  echo
  echo "Redeploy failed. Check the command output above."
  exit 1
}

to_lower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

do_observability_server() {
  echo "[build] mvn clean package..."
  cd "$ROOT_DIR/microservices/observability-server" || return 1
  mvn clean package || return 1
  echo "[build] docker image observability-server:$IMAGE_TAG..."
  docker build --no-cache -t observability-server:$IMAGE_TAG . || return 1
  cd "$ROOT_DIR" || return 1
  echo "[deploy] kubectl apply + set image..."
  kubectl apply -f "$ROOT_DIR/k8s/observability-server" || return 1
  kubectl set image deployment/observability-server observability-server=observability-server:$IMAGE_TAG -n observability || return 1
  kubectl rollout status deployment/observability-server -n observability --timeout=180s
}

do_observability_debug_agent() {
  echo "[build] docker image observability-debug-agent:$IMAGE_TAG (API + chat UI)..."
  cd "$ROOT_DIR/microservices/observability-debug-agent" || return 1
  docker build --no-cache -t observability-debug-agent:$IMAGE_TAG . || return 1
  cd "$ROOT_DIR" || return 1
  echo "[deploy] kubectl apply + set image..."
  kubectl apply -f "$ROOT_DIR/k8s/observability-debug-agent/configmap.yaml" || return 1
  kubectl apply -f "$ROOT_DIR/k8s/observability-debug-agent/deployment.yaml" || return 1
  kubectl apply -f "$ROOT_DIR/k8s/observability-debug-agent/service.yaml" || return 1
  kubectl set image deployment/observability-debug-agent observability-debug-agent=observability-debug-agent:$IMAGE_TAG -n observability || return 1
  kubectl rollout status deployment/observability-debug-agent -n observability --timeout=180s
}

do_ecommerce() {
  echo "[build] mvn clean package..."
  cd "$ROOT_DIR/microservices/ecommerce" || return 1
  mvn clean package || return 1
  echo "[build] docker image ecommerce:$IMAGE_TAG..."
  docker build --no-cache -t ecommerce:$IMAGE_TAG . || return 1
  cd "$ROOT_DIR" || return 1
  kubectl apply -f "$ROOT_DIR/k8s/ecommerce" || return 1
  kubectl set image deployment/ecommerce ecommerce=ecommerce:$IMAGE_TAG -n ecommerce || return 1
  kubectl rollout status deployment/ecommerce -n ecommerce --timeout=180s
}

do_product() {
  echo "[build] mvn clean package..."
  cd "$ROOT_DIR/microservices/product" || return 1
  mvn clean package || return 1
  echo "[build] docker image product-service:$IMAGE_TAG..."
  docker build --no-cache -t product-service:$IMAGE_TAG . || return 1
  cd "$ROOT_DIR" || return 1
  kubectl apply -f "$ROOT_DIR/k8s/product" || return 1
  kubectl set image deployment/product product=product-service:$IMAGE_TAG -n ecommerce || return 1
  kubectl rollout status deployment/product -n ecommerce --timeout=180s
}

do_images() {
  echo "[build] mvn clean package..."
  cd "$ROOT_DIR/microservices/images" || return 1
  mvn clean package || return 1
  echo "[build] docker image images:$IMAGE_TAG..."
  docker build --no-cache -t images:$IMAGE_TAG . || return 1
  cd "$ROOT_DIR" || return 1
  kubectl apply -f "$ROOT_DIR/k8s/images" || return 1
  kubectl set image deployment/images images=images:$IMAGE_TAG -n ecommerce || return 1
  kubectl rollout status deployment/images -n ecommerce --timeout=180s
}

do_grafana() {
  echo "[deploy] grafana manifests (config/dashboard changes; upstream image)..."
  kubectl apply -f "$ROOT_DIR/k8s/observability/grafana" || return 1
  kubectl rollout restart deployment/grafana -n observability || return 1
  kubectl rollout status deployment/grafana -n observability --timeout=120s
}

do_prometheus() {
  echo "[deploy] prometheus manifests (config changes; upstream image)..."
  kubectl apply -f "$ROOT_DIR/k8s/observability/prometheus" || return 1
  kubectl rollout restart deployment/prometheus -n observability || return 1
  kubectl rollout status deployment/prometheus -n observability --timeout=120s
}

do_loki() {
  echo "[deploy] loki manifests (config changes; upstream image)..."
  kubectl apply -f "$ROOT_DIR/k8s/observability/loki" || return 1
  kubectl rollout restart deployment/loki -n observability || return 1
  kubectl rollout status deployment/loki -n observability --timeout=120s
}

do_promtail() {
  echo "[deploy] promtail manifests (config changes; upstream image)..."
  kubectl apply -f "$ROOT_DIR/k8s/observability/promtail" || return 1
  kubectl rollout restart daemonset/promtail -n observability || return 1
  kubectl rollout status daemonset/promtail -n observability --timeout=120s
}

do_ingress() {
  echo "[deploy] ingress manifests..."
  kubectl apply -f "$ROOT_DIR/k8s/ingress"
}

redeploy() {
  local name
  name="$(to_lower "$1")"
  if [ "$name" = "graphana" ]; then
    name="grafana"
  fi

  case "$name" in
    observability-server) do_observability_server ;;
    observability-debug-agent) do_observability_debug_agent ;;
    ecommerce|ecommerce-service) do_ecommerce ;;
    product|product-service) do_product ;;
    images|images-service) do_images ;;
    grafana) do_grafana ;;
    prometheus) do_prometheus ;;
    loki) do_loki ;;
    promtail) do_promtail ;;
    ingress) do_ingress ;;
    *)
      echo
      echo "ERROR: Unknown service \"$name\"."
      usage
      return 1
      ;;
  esac
}

if [ "$#" -eq 0 ]; then
  usage
  exit 0
fi

first_lower="$(to_lower "$1")"
case "$first_lower" in
  --help|-h|help|\?)
    usage
    exit 0
    ;;
esac

cd "$ROOT_DIR" || fail

echo "Using image tag $IMAGE_TAG for custom-built services."
echo

REQUESTED_COUNT=0
SUCCESS_COUNT=0

for raw_svc in "$@"; do
  svc="${raw_svc// /}"
  REQUESTED_COUNT=$((REQUESTED_COUNT + 1))
  echo "============================================================"
  echo "Redeploying: $svc"
  echo "============================================================"
  redeploy "$svc" || fail
  SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
  echo
done

echo "============================================================"
echo "Redeploy complete: $SUCCESS_COUNT service(s), tag $IMAGE_TAG"
echo "============================================================"
echo
kubectl get pods -n ecommerce 2>/dev/null
echo
kubectl get pods -n observability 2>/dev/null
