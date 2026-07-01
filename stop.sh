#!/usr/bin/env bash

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

fail() {
  echo
  echo "Shutdown failed. Check the command output above."
  exit 1
}

echo "[1/4] Switching to repo root..."
cd "$ROOT_DIR" || fail

echo "[2/4] Removing observability resources..."
kubectl delete -f "$ROOT_DIR/k8s/observability-debug-agent/configmap.yaml" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability-debug-agent/deployment.yaml" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability-debug-agent/service.yaml" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability-server" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability/grafana" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability/promtail" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability/loki" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability/prometheus" --ignore-not-found

echo "[3/4] Removing application resources..."
kubectl delete -f "$ROOT_DIR/k8s/ingress" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/ecommerce" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/images" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/product" --ignore-not-found

echo "[4/4] Current namespace status..."
kubectl get pods -n ecommerce --ignore-not-found
kubectl get pods -n observability --ignore-not-found
kubectl delete namespace observability-agent --ignore-not-found

echo
echo "Shutdown complete."
