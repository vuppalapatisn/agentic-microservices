# Coding Agent Reference

**Repository:** `microservices-ecommerce-2` (local Kubernetes ecommerce + observability demo)  
**Primary languages:** Java 21 (Spring Boot), Python 3 (FastAPI), TypeScript (React chat UI)  
**License:** MIT

Use this file as the **single technical reference** when changing code in this repo. If this file conflicts with an old comment or plan doc, **trust the code paths listed here**.

---

## 1. What this repo is

| Layer | Components |
|-------|------------|
| **Business microservices (3)** | `ecommerce`, `product`, `images` — namespace `ecommerce` |
| **Observability services (2)** | `observability-agent` (Java REST + MCP), `talk-to-observability-agent` (Python LangGraph + chat UI) — namespace `observability` |
| **Observability stack** | Prometheus, Loki, Promtail, Grafana — namespace `observability` |
| **Orchestration** | `start.bat` / `stop.bat` / `restart--redeploy-service.bat` (Windows); Kubernetes only (no docker-compose for apps) |

**Not in this repo:** `coupon-service` is referenced by ecommerce but **never deployed** (intentional demo failure).

---

## 2. Service catalog (authoritative)

### 2.1 Ecommerce microservices

| Service | K8s deployment | Context path | Port | Namespace |
|---------|----------------|--------------|------|-----------|
| ecommerce | `ecommerce` | `/ecommerce-service` | 8090 | `ecommerce` |
| product | `product` | `/product-service` | 8090 | `ecommerce` |
| images | `images` | `/image-service` | 8090 | `ecommerce` |

**Ecommerce role:** Aggregates product + optional image data; exposes demo endpoints.

| Method | Path (after context path) | Purpose |
|--------|---------------------------|---------|
| `GET` | `/ecommerceProducts` | Main catalog API |
| `POST` | `/apply-coupon` | Demo error path (`Content-Type: text/plain`, body = exactly 6 alphanumeric chars, e.g. `DISC20`) |

**Coupon flow (demo only):**
- `CouponClient` → `GET {services.coupon.base-url}/coupons/{code}` (default `http://coupon-service:8090`)
- On failure: `log.error("coupon_apply_failed couponCode={} targetUrl={}", ...)` with stack trace; HTTP **502** + JSON `CouponApplyErrorResponse`
- Config: `services.coupon.base-url` in `application.properties`; env `SERVICES_COUPON_BASE_URL` in `k8s/ecommerce/configmap.yaml`

**Product / images:** H2 in-memory DB; SQL init via `schema.sql` + `data.sql` (see §8).

### 2.2 Observability-agent (Java)

| Item | Value |
|------|--------|
| Source | `microservices/observability-agent/` |
| K8s | `k8s/observability-agent/` |
| Base path | `/api/observability` |
| Port | 8091 (cluster); local Swagger often needs port-forward |

**REST endpoints (talk-to-observability-agent calls these via HTTP, not MCP):**

| Method | Path | Query params | Returns |
|--------|------|--------------|---------|
| `GET` | `/logs/request/{correlationId}` | `startTime`, `endTime` (ISO-8601, optional) | Logs for correlation ID (Loki, namespaces `ecommerce` + `observability`) |
| `GET` | `/logs/service/{serviceName}` | `startTime`, `endTime` | Service logs |
| `GET` | `/logs/errors/{serviceName}` | `startTime`, `endTime` | ERROR/WARN logs |
| `GET` | `/metrics/heap/{serviceName}` | `startTime`, `endTime`, `stepSeconds` | Heap **used** (PromQL below) |
| `GET` | `/metrics/heap-max/{serviceName}` | `startTime`, `endTime`, `stepSeconds` | Heap **max** (PromQL below) |
| `GET` | `/metrics/threads/{serviceName}` | `startTime`, `endTime`, `stepSeconds` | Live threads |
| `GET` | `/metrics/request-rate/{serviceName}` | `startTime`, `endTime`, `stepSeconds` | HTTP request rate |
| `GET` | `/services` | — | Static list: `product-service`, `images-service`, `ecommerce-service` |

**PromQL passed to Prometheus (do not regress):**

| Metric API | Metric expression in `ObservabilityService` | Resolved example (ecommerce) |
|------------|---------------------------------------------|----------------------------|
| heap | `sum(jvm_memory_used_bytes)` | `sum(jvm_memory_used_bytes{job="ecommerce",area="heap"})` |
| heap-max | `sum(jvm_memory_max_bytes)` | `sum(jvm_memory_max_bytes{job="ecommerce",area="heap"})` |
| request-rate | `sum(rate(http_server_requests_seconds_count[1m]))` | `sum(rate(http_server_requests_seconds_count{job="ecommerce"}[1m]))` |
| threads | `jvm_threads_live_threads` | `jvm_threads_live_threads{job="ecommerce"}` |

**Service name → Prometheus `job`:** `ecommerce-service` → `ecommerce` (strip `-service` suffix in `PrometheusClient.toJobName()`).

**MCP tools** (`ObservabilityTools`, Spring AI): same capabilities as REST except **no `get_heap_max_metrics` tool** — only REST has heap-max. MCP is optional for external clients; **talk-to uses REST only**.

### 2.3 Talk-to-observability-agent (Python + UI)

| Item | Value |
|------|--------|
| Source | `microservices/talk-to-observability-agent/` |
| UI | `microservices/talk-to-observability-agent/ui/` (React + Vite) |
| K8s | `k8s/talk-to-observability-agent/` |
| Port | 8092 (NodePort / ingress to localhost) |

**HTTP API:**

| Method | Path | Body |
|--------|------|------|
| `GET` | `/health` | — |
| `POST` | `/api/v1/investigate` | `{ "query": string, "correlationId"?: string }` |
| `GET` | `/` | Chat UI (static, baked into Docker image) |
| — | `/docs` | OpenAPI / Swagger |

**Processing pipeline:**
1. LangGraph workflow (`app/graph/workflow.py`)
2. Keyword classification (`app/graph/classification.py`) → `needs_logs`, `needs_monitoring`, `heap_usage_percent_query`, `fetch_*`
3. Conditional fetches via `ObservabilityAgentClient` (`app/mcp/observability_client.py`) — **REST to observability-agent**
4. Deterministic correlation (`app/correlation/engine.py`)
5. OpenAI summary (`app/services/reasoning_service.py`) — prompt mode: `default` | `error_logs` | `heap_percent`

**Workflow diagram:** `microservices/talk-to-observability-agent/app/graph/workflow-diagram.md`

---

## 3. Investigation modes (must not break)

All three modes use **`POST /api/v1/investigate`** (or chat UI). Routing is **rule-based on lowercase query text** in `classify_investigation()` — no ML.

| Mode | Example query | `needs_logs` | `needs_monitoring` | `heap_usage_percent_query` | Fetches | Grafana in response |
|------|---------------|--------------|--------------------|-----------------------------|---------|---------------------|
| **1. Slow / traffic spike** | `Find reason for slowness for correlation id <uuid>` | Y | Y | N | logs + error logs + heap + threads + request rate | Explore + dashboard |
| **2. Error / coupon** | `Give me stack trace of error for request <uuid>` | Y | N | N | logs + error logs only | Explore only |
| **3. Heap %** | `What is the heap usage of ecommerce-service?` | N | Y | Y | heap used + heap max only | Dashboard only |

**Classification rules (summary):**
- Monitoring keywords → `needs_monitoring`: `slow`, `slowness`, `latency`, `timeout`, `heap`, `memory`, `thread`, `rate`, `rps`, `traffic`, `load`, `metric`, `metrics`, `prometheus`, `saturation`, `overload`, `spike`
- Log/error keywords → contribute to `needs_logs`: `error`, `fail`, `failure`, `exception`, `stack`, `trace`, `log`, `logs`, `404`, `500`, `502`, `coupon`, `details`
- Investigation keywords → also `needs_logs`: `slow`, `slowness`, `latency`, `timeout`, `correlation`, `request id`, `requestid`
- **Heap % detector:** monitoring + (`heap`|`memory`) + (`usage`|`used`|`percent`|`%`|`how much`) + **no** investigation keywords + **no** log/error keywords
- **Default** (no keywords): `needs_logs=Y`, `needs_monitoring=Y`
- **Critical:** `slow` / `slowness` / `latency` always → logs **and** monitoring (full path)

**Heap % answer:** Latest Prometheus point: `percent = used / max * 100`; evidence like `Heap usage is 42.3% (28.01 MB of 66.15 MB).`

**Demo steps:** `demo-usecases.md`  
**Traffic script:** `scripts/simulate_traffic_spike.py` — see `scripts/TRAFFIC_SPIKE.md`

---

## 4. Local URLs (localhost)

| What | URL |
|------|-----|
| Ecommerce products | http://localhost:8090/ecommerce-service/ecommerceProducts |
| Ecommerce apply-coupon | `POST` http://localhost:8090/ecommerce-service/apply-coupon |
| Ecommerce actuator | http://localhost:8090/ecommerce-service/actuator |
| Ecommerce Prometheus scrape | http://localhost:8090/ecommerce-service/actuator/prometheus |
| Grafana | http://localhost:3000 |
| Prometheus UI | http://localhost:9090 |
| Chat UI | http://localhost:8092 |
| Talk-to Swagger | http://localhost:8092/docs |
| Observability-agent Swagger | http://localhost:8091/swagger-ui.html *(requires port-forward to pod)* |

---

## 5. Inter-service DNS (required)

Use **Kubernetes DNS hostnames only** inside the cluster:

| Caller | Target | URL pattern |
|--------|--------|-------------|
| ecommerce | product | `http://product-service:8090` |
| ecommerce | images | `http://images-service:8090` |
| ecommerce | coupon (not deployed) | `http://coupon-service:8090` |
| talk-to | observability-agent | `http://observability-agent.observability.svc.cluster.local:8091` |
| observability-agent | Loki / Prometheus | from `k8s/observability-agent/configmap.yaml` |

**Do not reintroduce:** `HOST_IP`, Consul, nginx service discovery, or docker-compose for app wiring.

---

## 6. Build and deploy

### 6.1 Full stack

```bat
start.bat    REM build all images (timestamp tag), apply manifests, wait for rollouts
stop.bat     REM tear down workloads
```

`start.bat` uses a **new timestamp Docker tag every run** and updates deployments — this is required behavior (avoids stale images).

### 6.2 Single-service redeploy

```bat
restart--redeploy-service.bat <service> [service2 ...]
restart--redeploy-service.bat --help
```

**Custom-built (Maven + Docker + `kubectl set image`):**
- `ecommerce` (alias `ecommerce-service`)
- `product` (alias `product-service`)
- `images` (alias `images-service`)
- `observability-agent`
- `talk-to-observability-agent` (includes `npm run build` for UI in Dockerfile)

**Manifest-only rollouts (no app jar build):**
- `grafana`, `prometheus`, `loki`, `promtail`, `ingress`

### 6.3 Maven rule for coding agents

**Do not run Maven automatically.** After Java code changes, tell the user to run Maven (or use `restart--redeploy-service.bat`, which runs `mvn clean package` for Java services). Verify compile output from the user.

**Typical redeploy after Java + Python changes:**
```bat
restart--redeploy-service.bat ecommerce observability-agent talk-to-observability-agent
```

---

## 7. Kubernetes layout

| Path | Contents |
|------|----------|
| `k8s/namespace.yaml` | `ecommerce` namespace |
| `k8s/ecommerce/`, `k8s/product/`, `k8s/images/` | configmap, deployment, service each |
| `k8s/ingress/` | Ingress rules |
| `k8s/observability/namespace.yaml` | `observability` namespace |
| `k8s/observability/prometheus/`, `loki/`, `promtail/`, `grafana/` | Stack manifests |
| `k8s/observability-agent/` | Observability-agent |
| `k8s/talk-to-observability-agent/` | Talk-to + `secret-example.yaml` (OpenAI key template) |

Each app service manifest set: `configmap.yaml`, `deployment.yaml`, `service.yaml`.

---

## 8. Configuration sync rule

**Spring `application.properties` and `k8s/*/configmap.yaml` must stay aligned.**

Env vars use relaxed binding (e.g. `SERVICES_COUPON_BASE_URL` → `services.coupon.base-url`).

Talk-to config: `k8s/talk-to-observability-agent/configmap.yaml` — keys must match `app/config/settings.py`.

**One-time secret (survives `start.bat`):**
```powershell
kubectl create secret generic talk-to-observability-agent-secret `
  --from-literal=OPENAI_API_KEY=your-key-here `
  -n observability
```

**Talk-to environment variables:**

| Variable | Purpose | Default in code |
|----------|---------|-----------------|
| `OPENAI_API_KEY` | Required for investigate | — |
| `OPENAI_MODEL` | Chat model | `gpt-4.1-mini` |
| `OBSERVABILITY_AGENT_BASE_URL` | REST base | `http://observability-agent.observability.svc.cluster.local:8091` |
| `REQUEST_TIMEOUT_SECONDS` | HTTP client timeout | `10` |
| `STARTUP_VALIDATION_RETRIES` | Wait for observability-agent | `30` |
| `STARTUP_VALIDATION_RETRY_SECONDS` | Retry interval | `2` |
| `GRAFANA_BASE_URL` | Links in chat (browser) | `http://localhost:3000` |
| `GRAFANA_API_BASE_URL` | UID resolution (in-cluster) | `http://grafana.observability.svc.cluster.local:3000` |
| `GRAFANA_LOKI_DATASOURCE_UID` | Explore link | `loki` |
| `GRAFANA_DASHBOARD_UID` | Dashboard link | `ecommerce-observability` |

---

## 9. Observability conventions

### 9.1 Logging

- JSON to stdout (Logstash encoder)
- Fields: `timestamp`, `service`, `level`, `correlationId`, `thread`, `logger`, `message`
- **Correlation header:** `X-Correlation-Id` — generated if missing; propagated ecommerce → downstream via `RestTemplate` interceptor + MDC

### 9.2 Prometheus metrics scraped

From actuator `/actuator/prometheus` on each app service:

- `jvm_memory_used_bytes`, `jvm_memory_max_bytes`
- `jvm_threads_live_threads`
- `jvm_gc_pause_seconds_*`
- `http_server_requests_seconds_count`

Grafana dashboard: `k8s/observability/grafana/dashboard-configmap.yaml` (aligned with `sum(...)` queries above).

### 9.3 Loki

- App logs: `{namespace="ecommerce", app="<service>"}` where app is `ecommerce`, `product`, or `images`
- Correlation search: `{namespace="ecommerce"} |= "<correlationId>"`

---

## 10. Database init (product, images)

**Current required settings (do not revert without explicit redesign):**

```properties
spring.sql.init.mode=always
spring.jpa.defer-datasource-initialization=true
spring.jpa.hibernate.ddl-auto=none
```

Plus `schema.sql` and `data.sql` on classpath.

---

## 11. Technology baseline

| Area | Version / stack |
|------|-----------------|
| Java | 21 |
| Spring Boot (apps) | 3.3.5 |
| Spring Boot (observability-agent) | 3.x |
| Python | FastAPI, LangGraph, httpx, Pydantic |
| UI | React, Vite, TypeScript |
| Docker base (Java) | `eclipse-temurin:21-jdk-alpine` |
| Build | Maven (Java), pip (scripts), npm (UI, in Docker build) |

Common Java deps: `spring-boot-starter-web`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `logstash-logback-encoder`.  
Product/images add: `spring-boot-starter-data-jpa`, H2.

---

## 12. Feature development rules

1. **Do not break the three investigation modes** (§3). Run demos in `demo-usecases.md` after talk-to/workflow changes.
2. Keep REST API paths stable unless the task explicitly changes contracts.
3. Keep context paths: `/ecommerce-service`, `/product-service`, `/image-service`.
4. Use Kubernetes DNS for inter-service URLs.
5. When changing Java config, update **both** `application.properties` and the matching `k8s/*/configmap.yaml`.
6. For new Prometheus gauges used in investigations, use **`sum(...)` across JVM pools** where applicable (heap used/max).
7. Prefer **minimal diffs** — no new microservices unless requested; `coupon-service` stays undeployed.
8. **talk-to stays thin:** fetch telemetry → deterministic correlation → OpenAI last.
9. Local canonical deploy path is `start.bat` / `restart--redeploy-service.bat`, not ad-hoc `spring-boot:run` alone.
10. Do not add a custom actuator endpoint with id `prometheus` (use Boot’s built-in export).

---

## 13. Files to open first (by task)

| Task | Paths |
|------|--------|
| Ecommerce API / coupon | `microservices/ecommerce/.../controller/EcommerceController.java`, `client/CouponClient.java`, `config/ExternalConfig.java`, `k8s/ecommerce/configmap.yaml` |
| Investigation routing | `microservices/talk-to-observability-agent/app/graph/classification.py`, `workflow.py` |
| Correlation / evidence | `app/correlation/engine.py`, `app/util/formatting.py` |
| LLM prompts | `app/prompts/reasoning.py`, `app/prompts/error_logs.py` |
| Observability REST / PromQL | `microservices/observability-agent/.../ObservabilityService.java`, `PrometheusClient.java`, `ObservabilityController.java` |
| Talk-to HTTP client | `app/mcp/observability_client.py` |
| Chat UI | `microservices/talk-to-observability-agent/ui/src/` |
| Prometheus scrape | `k8s/observability/prometheus/configmap.yaml` |
| Grafana panels | `k8s/observability/grafana/dashboard-configmap.yaml` |
| Traffic demo | `scripts/simulate_traffic_spike.py`, `scripts/TRAFFIC_SPIKE.md` |

---

## 14. Documentation index

| Doc | Purpose |
|-----|---------|
| `README.md` | Quick start, URLs, OpenAI secret |
| `demo-usecases.md` | **All three investigation demos** (authoritative steps) |
| `DEV-Readme.md` | APIs, port-forwards, Loki/PromQL examples |
| `chatbot-ui-readme.md` | Chat UI usage and local UI dev |
| `architecture-diagram.md` | System Mermaid diagram |
| `microservices/talk-to-observability-agent/app/graph/workflow-diagram.md` | LangGraph routing |
| `scripts/TRAFFIC_SPIKE.md` | Traffic spike script |
| `coding-agent-reference.md` | This file |

---

## 15. Verification checklist

| Change type | Check |
|-------------|--------|
| Ecommerce | `GET http://localhost:8090/ecommerce-service/ecommerceProducts` |
| Coupon demo | `POST /apply-coupon` with `DISC20` → non-2xx + `X-Correlation-Id` |
| Metrics | `http://localhost:8090/ecommerce-service/actuator/prometheus` |
| Slow investigation | Traffic script → chat with slowness + correlation id → heap + RPS in evidence |
| Error investigation | apply-coupon → chat with error/stack wording → logs only, Explore only |
| Heap % | Chat: `What is the heap usage of ecommerce-service?` → percent + MB, no logs |
| Talk-to health | `http://localhost:8092/health` |

---

## 16. Known good vs known bad

### Known good

- Timestamp-tagged images via `start.bat` / `restart--redeploy-service.bat`
- Probes on `/actuator/health` with correct context-path prefix in K8s
- Boot 3 H2 init via SQL scripts (not Hibernate `ddl-auto=create`)
- `sum(jvm_memory_*_bytes)` and `sum(rate(http_server_requests_seconds_count[1m]))` in observability-agent
- Conditional LangGraph skips unused fetches
- Chat UI served from talk-to image at `:8092`

### Known bad (do not repeat)

- Static Docker tag reuse (`:latest` only) without `kubectl set image`
- `ddl-auto=create` conflicting with `schema.sql`
- Raw per-pool heap series without `sum()` for investigations
- `HOST_IP` / Consul / compose-based discovery
- Assuming `mvn spring-boot:run` proves Kubernetes image/config parity
- Deploying `coupon-service` for the error demo (breaks the intended failure mode)

---

## 17. Changelog (agent-relevant)

| Date | Change |
|------|--------|
| 2026-05-20 | Three investigation modes; conditional LangGraph; `heap-max` REST endpoint |
| 2026-05-20 | Ecommerce `POST /apply-coupon` + `CouponClient` → undeployed `coupon-service` |
| 2026-05-20 | PromQL fixes: `sum()` for heap and request rate |
| 2026-05-20 | Talk-to chat UI in Docker; Grafana link helpers in UI |
| 2026-05-20 | `restart--redeploy-service.bat` for selective redeploys |
| 2026-05-20 | Removed dead code: unused `list_observable_services` in talk-to workflow, `CorrelationFinding.tags`, duplicate coupon doc (content in `demo-usecases.md`) |

---

**Last updated:** 2026-05-20
