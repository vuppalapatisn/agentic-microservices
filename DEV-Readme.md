# Developer Guide — APIs, Swagger, Logs & Metrics

Local development reference after running `start.bat`. Assumes Docker Desktop Kubernetes with LoadBalancer services mapped to `localhost`.

## Prerequisites

- Java 21, Maven, Docker Desktop, `kubectl`
- Stack running: `start.bat` from repo root
- For AI investigations: create secret once (not removed by `start.bat`):

```powershell
kubectl create secret generic talk-to-observability-agent-secret `
  --from-literal=OPENAI_API_KEY=your-key-here `
  -n observability
```

---

## Quick URL reference

| What | URL | Access |
|------|-----|--------|
| Ecommerce API | http://localhost:8090/ecommerce-service/ecommerceProducts | LoadBalancer |
| Product API | port-forward (see below) | ClusterIP |
| Images API | port-forward (see below) | ClusterIP |
| Observability Agent Swagger | http://localhost:8091/swagger-ui.html | port-forward required |
| Talk To Observability Swagger | http://localhost:8092/docs | LoadBalancer |
| Prometheus UI | http://localhost:9090 | LoadBalancer |
| Grafana UI | http://localhost:3000 | LoadBalancer |

---

## Testing APIs with Swagger / OpenAPI

### Services with interactive API docs

| Service | Swagger / OpenAPI UI | OpenAPI JSON | Notes |
|---------|----------------------|--------------|-------|
| **observability-agent** | http://localhost:8091/swagger-ui.html | http://localhost:8091/v3/api-docs | SpringDoc (springdoc-openapi) |
| **talk-to-observability-agent** | http://localhost:8092/docs | http://localhost:8092/openapi.json | FastAPI built-in UI |

### Application microservices (no Swagger UI)

`ecommerce`, `product`, and `images` do not ship SpringDoc/Swagger. Use the REST URLs below or Spring Actuator for health/metrics discovery.

| Service | Context path | Main REST endpoint | Actuator index |
|---------|--------------|-------------------|----------------|
| **ecommerce** | `/ecommerce-service` | `GET /ecommerce-service/ecommerceProducts` | http://localhost:8090/ecommerce-service/actuator |
| **product** | `/product-service` | `GET /product-service/products` | http://localhost:8090/product-service/actuator (via port-forward) |
| **images** | `/image-service` | `GET /image-service/images` | http://localhost:8090/image-service/actuator (via port-forward) |

**Example — ecommerce (browser or curl):**

```powershell
curl http://localhost:8090/ecommerce-service/ecommerceProducts
curl http://localhost:8090/ecommerce-service/actuator/health
```

**Example — product & images (port-forward first):**

```powershell
kubectl port-forward -n ecommerce svc/product-service 8090:8090
# new terminal
curl http://localhost:8090/product-service/products
curl http://localhost:8090/product-service/actuator/health

kubectl port-forward -n ecommerce svc/images-service 8090:8090
# new terminal (use another local port if 8090 is busy, e.g. 8093:8090)
curl http://localhost:8090/image-service/images
```

---

## observability-agent — Swagger

**1. Port-forward** (service is `ClusterIP` in namespace `observability`):

```powershell
kubectl port-forward -n observability svc/observability-agent 8091:8091
```

**2. Open Swagger UI:** http://localhost:8091/swagger-ui.html

**3. Try endpoints** (use **Try it out** in Swagger):

| Endpoint | Example |
|----------|---------|
| `GET /api/observability/services` | Lists `product-service`, `images-service`, `ecommerce-service` |
| `GET /api/observability/logs/service/{serviceName}` | `serviceName=ecommerce-service` |
| `GET /api/observability/logs/errors/{serviceName}` | `serviceName=product-service` |
| `GET /api/observability/logs/request/{requestId}` | Correlation ID from app logs |
| `GET /api/observability/metrics/heap/{serviceName}` | Optional: `startTime`, `endTime`, `stepSeconds` |
| `GET /api/observability/metrics/threads/{serviceName}` | Same query params |
| `GET /api/observability/metrics/request-rate/{serviceName}` | Same query params |

**Time range query params** (optional, ISO-8601):

- `startTime=2026-05-20T00:00:00Z`
- `endTime=2026-05-20T23:59:59Z`
- `stepSeconds=60` (metrics only)

**Health:** http://localhost:8091/actuator/health

---

## talk-to-observability-agent — Swagger (FastAPI)

No port-forward needed if LoadBalancer is ready.

| URL | Purpose |
|-----|---------|
| http://localhost:8092/docs | Swagger UI |
| http://localhost:8092/redoc | ReDoc |
| http://localhost:8092/health | Health check |

**Example investigation** (Swagger **POST /api/v1/investigate** or curl):

```powershell
curl -X POST http://localhost:8092/api/v1/investigate `
  -H "Content-Type: application/json" `
  -d "{\"query\": \"Why is ecommerce slow in the last hour?\"}"
```

Requires `talk-to-observability-agent-secret` with a valid `OPENAI_API_KEY` in namespace `observability`.

### Correlation ID tracing

Every HTTP request across services uses header **`X-Correlation-Id`** (UUID). The same ID is echoed on the response and written to JSON logs as `correlationId`.

| Service | How correlation ID is set |
|---------|---------------------------|
| ecommerce, product, images | `CorrelationIdFilter` — generates UUID if header missing |
| observability-agent | `CorrelationIdFilter` — same behavior |
| talk-to-observability-agent | Middleware — forwards header to observability-agent |

**Trace a failed investigation (503):**

1. Note `X-Correlation-Id` from the HTTP response (or Swagger response headers).
2. Search talk-to logs: `{namespace="observability", app="talk-to-observability-agent"} |= "<correlation-id>"`
3. Search observability-agent logs with the same ID.
4. Search app logs: `{namespace="ecommerce"} |= "<correlation-id>"`

**503 does not mean the pod is down.** `/health` only checks the process is running. `POST /api/v1/investigate` returns **503** when a `RuntimeError` occurs — usually:

- `observability-agent` unreachable or returned 4xx/5xx (Loki/Prometheus errors)
- `OPENAI_API_KEY` missing or OpenAI API failure

Check JSON log `investigation_failed` for the `error` field, or call the API and read the response `detail` body.

---

## Log aggregation (Loki + Grafana)

Logs are collected by **Promtail** → **Loki** → viewed in **Grafana**.

### Open Grafana

1. Browser: **http://localhost:3000**
2. First login (default install): user `admin`, password `admin` (you may be asked to change it).

### Explore logs (Loki)

1. Left menu → **Explore**
2. Datasource: **Loki**
3. Switch to **Code** mode and run LogQL examples:

**All logs for ecommerce service:**

```logql
{namespace="ecommerce", app="ecommerce"}
```

**Product service errors:**

```logql
{namespace="ecommerce", app="product"} |~ "(?i)(ERROR|WARN)"
```

**Trace a request by correlation ID** (replace with a real ID from logs):

```logql
{namespace="ecommerce"} |= "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

**Observability agent logs:**

```logql
{namespace="observability", app="observability-agent"}
```

**Filter by log level in JSON:**

```logql
{namespace="ecommerce", app="ecommerce"} | json | level="ERROR"
```

### Tips

- Set time range (top right) to **Last 15 minutes** or **Last 1 hour** after generating traffic.
- Generate traffic: `curl http://localhost:8090/ecommerce-service/ecommerceProducts` several times.
- Mock/generated logs may also appear under `job="generated-logs"` labels in Explore.

### Loki API (optional, advanced)

Port-forward if you need raw Loki API access:

```powershell
kubectl port-forward -n observability svc/loki 3100:3100
curl -G "http://localhost:3100/loki/api/v1/query_range" `
  --data-urlencode 'query={namespace="ecommerce",app="ecommerce"}' `
  --data-urlencode 'limit=10'
```

---

## Monitoring (Prometheus + Grafana)

### Prometheus UI

**URL:** http://localhost:9090

**Example PromQL queries** (tab **Graph** → enter query → **Execute**):

| Goal | Query |
|------|-------|
| Ecommerce heap used | `jvm_memory_used_bytes{job="ecommerce",area="heap"}` |
| Product thread count | `jvm_threads_live_threads{job="product"}` |
| Images request rate | `rate(http_server_requests_seconds_count{job="images"}[1m])` |
| GC pause rate (ecommerce) | `rate(jvm_gc_pause_seconds_sum{job="ecommerce"}[5m])` |

**Targets / scrape health:** http://localhost:9090/targets — expect `ecommerce`, `product`, `images` jobs **UP**.

**App metrics endpoints** (scraped by Prometheus):

- http://localhost:8090/ecommerce-service/actuator/prometheus
- Product/images: via port-forward to respective services (same path pattern with their context path).

### Grafana dashboards

**URL:** http://localhost:3000

Pre-provisioned dashboard (if loaded):

1. **Dashboards** → browse for **Ecommerce Observability**
2. Panels include heap used/max, thread count, GC activity (Prometheus datasource).

**Build a quick panel in Explore (Prometheus datasource):**

1. **Explore** → datasource **Prometheus**
2. Example: `jvm_memory_used_bytes{job="ecommerce",area="heap"}`
3. **Run query** → **Add to dashboard** if you want to save it.

### Metrics via observability-agent Swagger

Same port-forward as Swagger (`8091`), then use metric endpoints in Swagger UI, e.g.:

- `GET /api/observability/metrics/heap/ecommerce-service?stepSeconds=60`

---

## Port-forward cheat sheet

Use when a service is `ClusterIP` only:

```powershell
# Observability agent (Swagger + REST)
kubectl port-forward -n observability svc/observability-agent 8091:8091

# Product
kubectl port-forward -n ecommerce svc/product-service 8090:8090

# Images (use 8093 locally if 8090 is taken)
kubectl port-forward -n ecommerce svc/images-service 8093:8090

# Loki API (optional)
kubectl port-forward -n observability svc/loki 3100:3100
```

---

## Kubernetes namespaces

| Namespace | Services |
|-----------|----------|
| `ecommerce` | ecommerce, product, images, ingress |
| `observability` | prometheus, loki, promtail, grafana, observability-agent, talk-to-observability-agent |

**Check pods:**

```powershell
kubectl get pods -n ecommerce
kubectl get pods -n observability
```

**Tail logs:**

```powershell
kubectl logs -n ecommerce deploy/ecommerce -f
kubectl logs -n observability deploy/observability-agent -f
kubectl logs -n observability deploy/talk-to-observability-agent -f
```

---

## End-to-end dev workflow example

1. `start.bat`
2. Hit app: http://localhost:8090/ecommerce-service/ecommerceProducts
3. Open Grafana → Explore → Loki → `{namespace="ecommerce", app="ecommerce"}`
4. Open Prometheus → `jvm_memory_used_bytes{job="ecommerce",area="heap"}`
5. Port-forward observability-agent → http://localhost:8091/swagger-ui.html → try `GET /api/observability/logs/service/ecommerce-service`
6. Open http://localhost:8092/docs → `POST /api/v1/investigate` with a natural-language question

---

## See also

- [README.md](README.md) — start/stop and high-level URLs
- [microservices/observability-agent/README.md](microservices/observability-agent/README.md) — observability-agent build & Swagger
- [microservices/talk-to-observability-agent/README.md](microservices/talk-to-observability-agent/README.md) — investigation API
