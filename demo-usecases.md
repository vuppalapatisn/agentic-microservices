# Demo use cases

Three investigation modes through the **same** chat UI and `POST /api/v1/investigate`. Query wording drives routing (logs only, metrics only, or both).

| # | Use case | Trigger | Example chat query | Routing |
|---|----------|---------|-------------------|---------|
| 1 | Slow requests | Traffic spike script | `Find reason for slowness for correlation id <uuid>` | Logs + metrics |
| 2 | Error details in logs | `POST /apply-coupon` | `Give me details of error for request <uuid>` | Logs only |
| 3 | Heap usage % | None (live metrics) | `What is the heap usage of ecommerce-service?` | Metrics only (heap used + max) |

## Prerequisites

1. Stack running: `start.bat`
2. One-time OpenAI secret in namespace `observability` (see [README.md](README.md))
3. Chat UI: http://localhost:8092 (or Swagger at http://localhost:8092/docs)

Optional: `pip install -r scripts/requirements.txt` for the traffic script.

After code changes, rebuild Java services with Maven, then:

```bat
restart--redeploy-service.bat ecommerce observability-server talk-to-observability-agent
```

---

## Use case 1 — Slow requests (traffic spike)

**Goal:** Correlate a slow or timed-out request with request-rate spikes, heap pressure, and thread growth. Response includes **both** Grafana Explore (logs) and the JVM metrics dashboard.

### Step 1 — Generate load

```powershell
python scripts/simulate_traffic_spike.py
```

Watch stdout for a slow or timeout line, for example:

```text
timestamp=... correlationId=<uuid> status=timeout responseTime=3000ms error=timeout
```

Copy the `correlationId`. More detail: [scripts/TRAFFIC_SPIKE.md](scripts/TRAFFIC_SPIKE.md).

### Step 2 — Confirm signals (optional)

| Signal | Where |
|--------|--------|
| Request-rate spike | Grafana **Request Rate** panel |
| Heap rise | Grafana **Heap Space** panel |
| Slow requests | Loki: `{namespace="ecommerce", app="ecommerce"} \|= "<uuid>"` |

### Step 3 — Investigate in chat

Open http://localhost:8092 and use one of:

- `Why is the ecommerce service slow in the last 15 minutes?`
- `Find reason for slowness for correlation id <uuid>`
- Paste `<uuid>` in the **correlation ID** field and ask: `slow request last 30 minutes`

### Expected result

- Evidence mentions **request rate**, **heap**, and/or **threads** (not logs-only).
- `probableRootCause` often **resource saturation** or **traffic overload**.
- Reply includes **Grafana Explore** (when correlation id is present) **and** **metrics dashboard** links.

---

## Use case 2 — Error details in logs (coupon failure)

**Goal:** Explain a failed request from Loki only—no Prometheus metrics. Purpose is for agent to understand an error from logs and elaborate on it.; we call a non-existant service from ecommerce-service that results in 404. The agent explains the error based on stack trace in logs.

### Step 1 — Trigger the error

**Windows CMD or PowerShell** — use **one line** (backtick `` ` `` line breaks are PowerShell-only; in CMD they run as separate broken commands):

```bat
curl.exe -i -X POST http://localhost:8090/ecommerce-service/apply-coupon -H "Content-Type: text/plain" -d DISC20
```

**PowerShell alternative** (`curl` is an alias for `Invoke-WebRequest`; use `curl.exe` or):

```powershell
Invoke-WebRequest -Uri "http://localhost:8090/ecommerce-service/apply-coupon" -Method POST -ContentType "text/plain" -Body "DISC20"
```

To see response headers including correlation id:

```powershell
$r = Invoke-WebRequest -Uri "http://localhost:8090/ecommerce-service/apply-coupon" -Method POST -ContentType "text/plain" -Body "DISC20" -SkipHttpErrorCheck
$r.Headers["X-Correlation-Id"]
$r.StatusCode
```

- Expect **502 Bad Gateway** (or similar non-2xx), not **415** (415 means `Content-Type: text/plain` or body was not sent — usually a split/broken multi-line command).
- Copy **`X-Correlation-Id`** from the response headers.

Log line shape (in Loki):

```text
coupon_apply_failed couponCode=DISC20 targetUrl=http://coupon-service:8090/coupons/DISC20
```

### Step 2 — Verify in Loki (optional)

Grafana **Explore → Loki**:

```logql
{namespace="ecommerce"} |= "<your-correlation-id>"
```

You should see ERROR lines with stack trace and `coupon-service` / connection failure.

### Step 3 — Investigate in chat

Use **error-focused** wording (do not use “slow” or “slowness”):

- `Give me details of error for request <correlation-id>`
- `Give me stack trace of error for request <correlation-id>`

### Expected result

- Summary explains downstream **coupon-service** unreachable or not deployed, with stack trace excerpt from logs.
- Evidence is **log-based** only—no heap, threads, or request-rate lines.
- **Grafana Explore** link present; **no** metrics dashboard link.

---

## Use case 3 — Heap usage percentage

**Goal:** Report current JVM heap as **% of max** (e.g. `42.3% (28.01 MB of 66.15 MB)`), using the latest Prometheus points—not logs.

### Step 1 — Ensure ecommerce is running

No special trigger. Normal traffic or idle ecommerce is enough for scrape metrics.

```powershell
curl http://localhost:8090/ecommerce-service/ecommerceProducts
```

### Step 2 — Investigate in chat

Ask a **heap usage** question without slow/error keywords, for example:

- `What is the heap usage of ecommerce-service?`
- `How much heap memory is ecommerce-service using?`

### Expected result

- Answer leads with **percentage**, then used and max sizes.
- No log excerpts; no request-rate or thread evidence.
- **Metrics dashboard** link in the reply; Explore link only if you also pass a correlation id (usually omitted for this query).

PromQL used internally: `sum(jvm_memory_used_bytes)` and `sum(jvm_memory_max_bytes)` for job `ecommerce`.

---

## Quick reference — chat vs routing

| If your query contains… | Logs fetched? | Metrics fetched? |
|-------------------------|---------------|------------------|
| `slow`, `slowness`, `latency`, `correlation id` (investigation) | Yes | Yes (full: heap, threads, RPS) |
| `error`, `stack`, `details`, `coupon` (no slow/latency) | Yes | No |
| `heap` + `usage` / `how much` / `%` (no slow/error words) | No | Yes (heap used + max only) |

Workflow diagram: [microservices/talk-to-observability-agent/app/graph/workflow-diagram.md](microservices/talk-to-observability-agent/app/graph/workflow-diagram.md).

## API alternative (Swagger)

`POST http://localhost:8092/api/v1/investigate`

```json
{
  "query": "Find reason for slowness for correlation id <uuid>",
  "correlationId": "<uuid>"
}
```

Use the same query strings as in the chat UI; `correlationId` is optional when the UUID appears in `query`.
