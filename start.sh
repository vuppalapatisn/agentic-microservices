#!/usr/bin/env bash

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_TAG="$(date +%Y%m%d%H%M%S)"

fail() {
  echo
  echo "Startup failed. Check the command output above."
  exit 1
}

echo "[1/12] Switching to repo root..."
cd "$ROOT_DIR" || fail

echo "[2/12] Removing old Kubernetes resources if present..."
kubectl delete -f "$ROOT_DIR/k8s/ingress" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/ecommerce" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/images" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/product" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability-debug-agent/configmap.yaml" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability-debug-agent/deployment.yaml" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability-debug-agent/service.yaml" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability-server" --ignore-not-found
kubectl delete namespace observability-agent --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability/grafana" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability/promtail" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability/loki" --ignore-not-found
kubectl delete -f "$ROOT_DIR/k8s/observability/prometheus" --ignore-not-found

echo "[3/12] Using image tag $IMAGE_TAG..."

echo "[4/12] Building observability-server..."
cd "$ROOT_DIR/microservices/observability-server" || fail
mvn clean package || fail
docker build --no-cache -t observability-server:$IMAGE_TAG . || fail

echo "[5/12] Building observability-debug-agent (API + chat UI)..."
cd "$ROOT_DIR/microservices/observability-debug-agent" || fail
docker build --no-cache -t observability-debug-agent:$IMAGE_TAG . || fail

echo "[6/12] Building product-service..."
cd "$ROOT_DIR/microservices/product" || fail
mvn clean package || fail
docker build --no-cache -t product-service:$IMAGE_TAG . || fail

echo "[7/12] Building images..."
cd "$ROOT_DIR/microservices/images" || fail
mvn clean package || fail
docker build --no-cache -t images:$IMAGE_TAG . || fail

echo "[8/12] Building ecommerce..."
cd "$ROOT_DIR/microservices/ecommerce" || fail
mvn clean package || fail
docker build --no-cache -t ecommerce:$IMAGE_TAG . || fail

echo "[9/12] Deploying application Kubernetes resources..."
cd "$ROOT_DIR" || fail
kubectl apply -f "$ROOT_DIR/k8s/namespace.yaml" || fail
kubectl apply -f "$ROOT_DIR/k8s/product" || fail
kubectl apply -f "$ROOT_DIR/k8s/images" || fail
kubectl apply -f "$ROOT_DIR/k8s/ecommerce" || fail
kubectl set image deployment/product product=product-service:$IMAGE_TAG -n ecommerce || fail
kubectl set image deployment/images images=images:$IMAGE_TAG -n ecommerce || fail
kubectl set image deployment/ecommerce ecommerce=ecommerce:$IMAGE_TAG -n ecommerce || fail
kubectl apply -f "$ROOT_DIR/k8s/ingress" || fail

echo "[10/12] Deploying observability stack..."
kubectl apply -f "$ROOT_DIR/k8s/observability/namespace.yaml" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/prometheus" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/loki" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/promtail" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability/grafana" || fail

echo "[11/12] Deploying observability services..."
kubectl apply -f "$ROOT_DIR/k8s/observability-server" || fail
kubectl set image deployment/observability-server observability-server=observability-server:$IMAGE_TAG -n observability || fail
kubectl delete deployment talk-to-observability-agent -n observability --ignore-not-found
kubectl delete service talk-to-observability-agent -n observability --ignore-not-found
kubectl delete configmap talk-to-observability-agent-config -n observability --ignore-not-found
kubectl apply -f "$ROOT_DIR/k8s/observability-debug-agent/configmap.yaml" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability-debug-agent/deployment.yaml" || fail
kubectl apply -f "$ROOT_DIR/k8s/observability-debug-agent/service.yaml" || fail
kubectl set image deployment/observability-debug-agent observability-debug-agent=observability-debug-agent:$IMAGE_TAG -n observability || fail

echo "[12/12] Waiting for application deployments..."
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
echo "Startup complete."
echo "Test with:"
echo "  kubectl logs -n ecommerce deploy/product"
echo "  kubectl logs -n ecommerce deploy/images"
echo "  kubectl logs -n ecommerce deploy/ecommerce"
echo "  kubectl logs -n observability deploy/observability-server"
echo "  kubectl logs -n observability deploy/observability-debug-agent"
echo "  curl http://localhost:3000"
echo "  curl http://localhost:8090/ecommerce-service/ecommerceProducts"
echo "  http://localhost:9090"
echo "  http://localhost:8092/health"
echo "  http://localhost:8092          (observability chatbot UI)"
echo "  http://localhost:8092/docs     (FastAPI Swagger)"
echo "  kubectl port-forward -n observability svc/observability-server 8091:8091"
echo "  http://localhost:8091/swagger-ui.html"
echo
echo "Chatbot UI: see chatbot-ui-readme.md"
