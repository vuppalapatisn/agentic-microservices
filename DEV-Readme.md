# Developer Guide

After `start.bat`. Assumes LoadBalancer → `localhost` (Docker Desktop K8s).

## Secret (one-time)

```bat
kubectl create secret generic observability-debug-agent-secret --from-literal=OPENAI_API_KEY=your-key-here -n observability
```

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

## Traffic spike simulation

5 rps × 30s → 400 rps × 180s → hard stop. Prints `correlationId` per request for Loki correlation.

```powershell
pip install -r scripts/requirements.txt
python scripts/simulate_traffic_spike.py
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

