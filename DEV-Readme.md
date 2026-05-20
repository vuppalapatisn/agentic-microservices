# Developer Guide

After `start.bat`. Assumes LoadBalancer → `localhost` (Docker Desktop K8s).

## Secret (one-time)

```powershell
kubectl create secret generic talk-to-observability-agent-secret `
  --from-literal=OPENAI_API_KEY=your-key-here `
  -n observability
```

## URLs

| Service | URL | Notes |
|---------|-----|-------|
| Ecommerce | http://localhost:8090/ecommerce-service/ecommerceProducts | LoadBalancer |
| Grafana | http://localhost:3000 | `admin` / `admin` on first login |
| Prometheus | http://localhost:9090 | |
| Talk-to-observability | http://localhost:8092/docs | FastAPI Swagger |
| Observability-agent | http://localhost:8091/swagger-ui.html | ClusterIP — port-forward below |

**Port-forward observability-agent:**

```powershell
kubectl port-forward -n observability svc/observability-agent 8091:8091
```

**Product / images** (ClusterIP): `kubectl port-forward -n ecommerce svc/product-service 8090:8090` (same for `images-service`).

## Swagger

| Service | UI |
|---------|-----|
| observability-agent | http://localhost:8091/swagger-ui.html |
| talk-to-observability-agent | http://localhost:8092/docs |

App services (ecommerce, product, images) have no Swagger — use REST/actuator URLs in README.

**Investigate example:**

```powershell
curl -X POST http://localhost:8092/api/v1/investigate `
  -H "Content-Type: application/json" `
  -d "{\"query\": \"Why is ecommerce slow?\"}"
```

## Correlation ID (`X-Correlation-Id`)

UUID on every request; echoed in response header and JSON logs as `correlationId`.

| Service | Mechanism |
|---------|-----------|
| ecommerce, product, images | `CorrelationIdFilter` |
| Service | Propagation | Logged (JSON `correlationId`) |
|---------|-------------|-------------------------------|
| ecommerce | `X-Correlation-Id` → product/images via RestTemplate | `RequestLoggingFilter` |
| product, images | inbound header | `RequestLoggingFilter` |
| observability-agent | `CorrelationIdFilter` + `RequestLoggingFilter` | yes |
| talk-to-observability-agent | middleware → observability-agent | yes |

**Loki (all ecommerce apps):**

```logql
{namespace="ecommerce"} |= "<correlation-id>"
```

**Investigate slow request:** use ID from traffic script in query or body `correlationId`:

```json
{"query": "slow request last 30 minutes", "correlationId": "<uuid-from-script>"}
```

**503 on `/api/v1/investigate`:** pod can be UP; check response `detail` and header `X-Correlation-Id`. Common causes: observability-agent/Loki/Prometheus error, missing `OPENAI_API_KEY`.

## Grafana / Loki (quick)

**Logs** — Explore → Loki (time range = last 15 min, after traffic):

```logql
{namespace="ecommerce", app="ecommerce"}
{namespace="ecommerce"} |= "<correlation-id>"
```

If empty: widen time range (e.g. **Last 6 hours**), generate traffic, then redeploy Promtail (`kubectl apply -f k8s/observability/promtail/configmap.yaml` + `kubectl rollout restart ds/promtail -n observability`). Log paths use `ecommerce_ecommerce-*` (dash after deployment name), not `ecommerce_ecommerce_*`.

**Metrics** — Explore → Prometheus or dashboard **Ecommerce Observability**:

```promql
rate(http_server_requests_seconds_count{job="ecommerce"}[1m])
jvm_memory_used_bytes{job="ecommerce",area="heap"}
```

## Traffic spike simulation

5 rps × 30s → 400 rps × 180s → hard stop. Prints `correlationId` per request for Loki correlation.

```powershell
pip install -r scripts/requirements.txt
python scripts/simulate_traffic_spike.py
```

Details: [scripts/TRAFFIC_SPIKE.md](scripts/TRAFFIC_SPIKE.md)

## Namespaces

| Namespace | Workloads |
|-----------|-----------|
| `ecommerce` | ecommerce, product, images, ingress |
| `observability` | prometheus, loki, promtail, grafana, observability-agent, talk-to-observability-agent |

```powershell
kubectl get pods -n ecommerce
kubectl get pods -n observability
kubectl logs -n observability deploy/talk-to-observability-agent -f
```
