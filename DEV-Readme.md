# Developer Guide

After `start.bat`. Assumes LoadBalancer → `localhost` (Docker Desktop K8s).

## Secrets (one-time)

```bat
kubectl create secret generic observability-debug-agent-secret --from-literal=OPENAI_API_KEY=your-key-here -n observability
kubectl create secret generic postgres-secret --from-literal=POSTGRES_PASSWORD=your-strong-password -n ecommerce
```

`postgres-secret` is required before `start.bat` (Postgres and the product/images pods won't start
without it). Both secrets survive `start.bat`/`stop.bat`.

## URLs


| Service                             | URL                                                                                                                    | Notes                                                               |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| Ecommerce                           | [http://localhost:8090/ecommerce-service/ecommerceProducts](http://localhost:8090/ecommerce-service/ecommerceProducts) | Main microservice that uses image service and product microservices |
| Grafana                             | [http://localhost:3000](http://localhost:3000)                                                                         | `admin` / `admin` on first login                                    |
| Prometheus                          | [http://localhost:9090](http://localhost:9090)                                                                         |                                                                     |
| observability-debug-agent (chat UI) | [http://localhost:8092](http://localhost:8092)                                                                         | React UI baked into image (`start.bat`)                             |
| observability-debug-agent (Swagger) | [http://localhost:8092/docs](http://localhost:8092/docs)                                                               | `POST /api/v1/investigate`                                          |
| observability-server                | [http://localhost:8091/swagger-ui.html](http://localhost:8091/swagger-ui.html)                                         | ClusterIP — port-forward below                                      |


Full chat UI guide: **[chatbot-ui-readme.md](chatbot-ui-readme.md)**

**Port-forward observability-server:**

```powershell
kubectl port-forward -n observability svc/observability-server 8091:8091
```

**Product / images** (ClusterIP): `kubectl port-forward -n ecommerce svc/product-service 8090:8090` (same for `images-service`).

## Postgres

Product + images use a `postgres` service (PostgreSQL 16, `ecommerce` namespace) with two databases:
`productsdb` and `imagesdb` (user `ecommerce`, password from the `postgres-secret` Secret). Data is
PVC-backed (`postgres-data`) and survives restarts; `stop.bat`/`start.bat` preserve the PVC + Secret.

```powershell
# open a psql shell inside the pod
kubectl exec -it -n ecommerce deploy/postgres -- psql -U ecommerce -d productsdb -c "SELECT product_id,name,category FROM product LIMIT 5;"
kubectl exec -it -n ecommerce deploy/postgres -- psql -U ecommerce -d imagesdb   -c "SELECT product_id,path FROM image LIMIT 5;"
# or port-forward and connect from the host
kubectl port-forward -n ecommerce svc/postgres 5432:5432
```

Runtime uses `schema-postgresql.sql` + `data-postgresql.sql` (idempotent — safe to re-seed on the
persistent volume). Tests use H2 via `schema-h2.sql` + `data-h2.sql` (`spring.sql.init.platform`).

## observability-debug-agent

Chat UI at **[http://localhost:8092](http://localhost:8092)** → `POST /api/v1/investigate`. Optional correlation ID from `scripts/simulate_traffic_spike.py`. Local UI dev: [chatbot-ui-readme.md](chatbot-ui-readme.md).

## Swagger


| Service                   | UI                                                                             |
| ------------------------- | ------------------------------------------------------------------------------ |
| observability-server      | [http://localhost:8091/swagger-ui.html](http://localhost:8091/swagger-ui.html) |
| observability-debug-agent | [http://localhost:8092/docs](http://localhost:8092/docs)                       |


App services (ecommerce, product, images) have no Swagger — use REST/actuator URLs in README.

**Investigate example:**

```powershell
curl -X POST http://localhost:8092/api/v1/investigate `
  -H "Content-Type: application/json" `
  -d "{\"query\": \"Why is ecommerce slow?\"}"
```

## Product search (Amazon-style)

Keyword + optional category search over an enriched catalog (`category`, `brand`, `stockQuantity`,
`rating`). The ecommerce endpoint fans out to product → images with the correlation id propagated.

```powershell
# product service (ClusterIP — port-forward first)
curl "http://localhost:8090/product-service/products/search?q=phone"
curl "http://localhost:8090/product-service/products/search?category=Electronics"
# ecommerce aggregator (adds image URLs)
curl "http://localhost:8090/ecommerce-service/ecommerceProducts/search?q=laptop"
```

At least one of `q` / `category` is required (else **HTTP 400**). Search feeds the load generator's
`--search-terms` mode below.

## Correlation ID (`X-Correlation-Id`)

UUID on every request; echoed in response header and JSON logs as `correlationId`.


| Service                   | Propagation                                              | Logged (`correlationId`) |
| ------------------------- | -------------------------------------------------------- | ------------------------ |
| ecommerce                 | `CorrelationIdFilter`; forwards header to product/images | `RequestLoggingFilter`   |
| product, images           | inbound `X-Correlation-Id`                               | `RequestLoggingFilter`   |
| observability-server      | `CorrelationIdFilter`                                    | `RequestLoggingFilter`   |
| observability-debug-agent | middleware → observability-server                        | yes                      |


**Loki (all ecommerce apps):**

```logql
{namespace="ecommerce"} |= "<correlation-id>"
```

**Investigate slow request:** use ID from traffic script in query or body `correlationId`:

```json
{"query": "slow request last 30 minutes", "correlationId": "<uuid-from-script>"}
```

**503 on `/api/v1/investigate`:** pod can be UP; check response `detail` and header `X-Correlation-Id`. Common causes: observability-server/Loki/Prometheus error, missing `OPENAI_API_KEY`.

## Grafana / Loki (quick)

**Logs** — Explore → Loki (time range = last 15 min, after traffic):

```logql
{namespace="ecommerce", app="ecommerce"}
{namespace="ecommerce"} |= "<correlation-id>"
```

If empty: widen time range (e.g. **Last 6 hours**), generate traffic, then redeploy Promtail (`kubectl apply -f k8s/observability/promtail/configmap.yaml` + `kubectl rollout restart ds/promtail -n observability`). Log paths use `ecommerce_ecommerce-`* (dash after deployment name), not `ecommerce_ecommerce_*`.

**Metrics** — Explore → Prometheus or dashboard **Ecommerce Observability**:

```promql
sum(rate(http_server_requests_seconds_count{job="ecommerce"}[1m]))
sum(jvm_memory_used_bytes{job="ecommerce",area="heap"})
sum(jvm_memory_max_bytes{job="ecommerce",area="heap"})
```

Dashboard **Heap Space** — used vs capacity. **Request Rate** — total RPS (same query as investigate API).

**Latency percentiles (P90/P95/P99)** — from the `http.server.requests` histogram buckets:

```promql
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{job="ecommerce"}[1m])) by (le))
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job="product"}[1m])) by (le))
```

Via observability-server (returns p50/p90/p95/p99 in one call):

```
GET http://localhost:8091/api/observability/metrics/latency-percentiles/ecommerce-service
```

Or just ask the agent: *"What are the P99 and P95 latencies for the ecommerce service in the last 15 minutes?"*

## Memory-based autoscaling (HPA)

`HorizontalPodAutoscaler`s scale product/images/ecommerce on memory (`AverageValue` 350Mi, 1→4
replicas; `k8s/<svc>/hpa.yaml`). They require a **metrics-server**, which is not bundled.

Install on Docker Desktop (the `--kubelet-insecure-tls` flag is required locally):

```powershell
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl patch deployment metrics-server -n kube-system --type=json `
  -p '[{\"op\":\"add\",\"path\":\"/spec/template/spec/containers/0/args/-\",\"value\":\"--kubelet-insecure-tls\"}]'
```

Watch scaling while driving search load:

```powershell
kubectl get hpa -n ecommerce -w
kubectl get pods -n ecommerce -w
python scripts/simulate_traffic_spike.py --search-terms "phone,laptop,shoes,coffee,watch"
```

Without metrics-server the HPAs report `<unknown>/350Mi` and simply don't scale (harmless).

## Traffic spike simulation

5 rps × 30s → 400 rps × 180s → hard stop. Prints `correlationId` per request for Loki correlation.

```powershell
pip install -r scripts/requirements.txt
python scripts/simulate_traffic_spike.py
# Drive the search endpoint (fans out ecommerce -> product -> images):
python scripts/simulate_traffic_spike.py --search-terms "phone,laptop,shoes,coffee,watch"
```

Details: [scripts/TRAFFIC_SPIKE.md](scripts/TRAFFIC_SPIKE.md)

## Namespaces


| Namespace       | Workloads                                                                            |
| --------------- | ------------------------------------------------------------------------------------ |
| `ecommerce`     | ecommerce, product, images, ingress                                                  |
| `observability` | prometheus, loki, promtail, grafana, observability-server, observability-debug-agent |


```powershell
kubectl get pods -n ecommerce
kubectl get pods -n observability
kubectl logs -n observability deploy/observability-debug-agent -f
```

