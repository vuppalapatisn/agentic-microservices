#!/usr/bin/env bash
#
# Runs every command documented in test.md against whatever cluster your
# CURRENT kubectl context points to (local Docker Desktop or IBM Cloud —
# whichever `kubectl config current-context` currently resolves to), and
# writes the real output to result.md in the repo root.
#
# Review result.md before committing it — cluster output can include
# internal pod/service names or data you may not want in git history.
#
# Usage:
#   ./capture-test-results.sh
#   git add result.md && git commit -m "Add captured test results" && git push

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$ROOT_DIR/result.md"
TS="$(date '+%Y-%m-%d %H:%M:%S %Z')"
CONTEXT="$(kubectl config current-context 2>/dev/null || echo 'unknown')"

PF_PIDS=()

cleanup() {
  for pid in "${PF_PIDS[@]}"; do
    kill "$pid" >/dev/null 2>&1
  done
}
trap cleanup EXIT

section() { printf '\n## %s\n\n' "$1" >> "$OUT"; }

run_capture() {
  local label="$1"
  local cmd="$2"
  local output
  output="$(bash -c "$cmd" 2>&1)"
  {
    echo "**$label**"
    echo
    echo '```'
    echo "\$ $cmd"
    echo "$output"
    echo '```'
    echo
  } >> "$OUT"
}

{
  echo "# Test Results"
  echo
  echo "Captured: $TS"
  echo "kubectl context: \`$CONTEXT\`"
} > "$OUT"

section "1. Unit tests (Java services)"
for svc in product images ecommerce observability-server; do
  if [ -d "$ROOT_DIR/microservices/$svc" ]; then
    pushd "$ROOT_DIR/microservices/$svc" >/dev/null
    run_capture "mvn test - $svc" "mvn -q test"
    popd >/dev/null
  fi
done

section "2. Cluster smoke checks"
run_capture "kubectl get pods -n ecommerce" "kubectl get pods -n ecommerce"
run_capture "kubectl get pods -n observability" "kubectl get pods -n observability"
run_capture "kubectl get svc -n ecommerce" "kubectl get svc -n ecommerce"
run_capture "kubectl get svc -n observability" "kubectl get svc -n observability"

section "3. Port-forwarding"
echo "Starting port-forwards against context: $CONTEXT" >> "$OUT"
kubectl port-forward -n ecommerce svc/product-service 8081:8090 >/dev/null 2>&1 &
PF_PIDS+=($!)
kubectl port-forward -n ecommerce svc/images-service 8082:8090 >/dev/null 2>&1 &
PF_PIDS+=($!)
kubectl port-forward -n ecommerce svc/ecommerce-service 8083:8090 >/dev/null 2>&1 &
PF_PIDS+=($!)
kubectl port-forward -n observability svc/observability-server 8091:8091 >/dev/null 2>&1 &
PF_PIDS+=($!)
kubectl port-forward -n observability svc/observability-debug-agent 8092:8092 >/dev/null 2>&1 &
PF_PIDS+=($!)
kubectl port-forward -n observability svc/grafana 3000:3000 >/dev/null 2>&1 &
PF_PIDS+=($!)
kubectl port-forward -n observability svc/prometheus 9090:9090 >/dev/null 2>&1 &
PF_PIDS+=($!)
sleep 5
echo "" >> "$OUT"
echo "Started ${#PF_PIDS[@]} port-forward processes (PIDs: ${PF_PIDS[*]})" >> "$OUT"

section "4. Endpoint tests"
run_capture "product-service actuator health" "curl -sS http://localhost:8081/actuator/health"
run_capture "image-service actuator health" "curl -sS http://localhost:8082/actuator/health"
run_capture "ecommerce-service actuator health" "curl -sS http://localhost:8083/actuator/health"
run_capture "GET /product-service/products" "curl -sS http://localhost:8081/product-service/products"
run_capture "GET /image-service/images" "curl -sS http://localhost:8082/image-service/images"
run_capture "GET /ecommerce-service/ecommerceProducts" "curl -sS http://localhost:8083/ecommerce-service/ecommerceProducts"
run_capture "POST /ecommerce-service/apply-coupon" "curl -sS -X POST http://localhost:8083/ecommerce-service/apply-coupon -H 'Content-Type: text/plain' -d 'ABC123'"

section "5. Observability stack"
run_capture "observability-server services" "curl -sS http://localhost:8091/api/observability/services"
run_capture "observability-debug-agent health" "curl -sS http://localhost:8092/health"
run_capture "Prometheus readiness" "curl -sS http://localhost:9090/-/ready"
run_capture "Grafana health" "curl -sS http://localhost:3000/api/health"

echo
echo "Wrote $OUT"
echo "Review it, then: git add result.md && git commit -m 'Add captured test results' && git push"
