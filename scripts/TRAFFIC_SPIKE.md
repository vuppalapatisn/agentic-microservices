# Traffic Spike Simulation

Sudden load burst for observability demos against `ecommerce-service`.

## Setup

```powershell
pip install -r scripts/requirements.txt
```

Ensure the stack is running (`start.bat`) and the API responds:

```powershell
curl http://localhost:8090/ecommerce-service/ecommerceProducts
```

## Run

Default profile (5 rps × 30s → 400 rps × 180s → stop):

```powershell
python scripts/simulate_traffic_spike.py
```

Aggressive spike (500 rps, 5 minutes):

```powershell
python scripts/simulate_traffic_spike.py --spike-rps 500 --spike-duration 300 --concurrency 600
```

From inside the cluster (same URL via service DNS):

```powershell
python scripts/simulate_traffic_spike.py --url http://ecommerce-service.ecommerce.svc.cluster.local:8090/ecommerce-service/ecommerceProducts
```

Press **Ctrl+C** for graceful shutdown.

## What you should see

| Signal | Where |
|--------|--------|
| Vertical request-rate spike | Grafana **Request Rate** panel or `sum(rate(http_server_requests_seconds_count{job="ecommerce"}[1m]))` |
| Heap increase | Grafana **Heap Space** panel: `sum(jvm_memory_used_bytes{...})` vs `sum(jvm_memory_max_bytes{...})` |
| Slow / failing requests | Script stdout + Loki `{namespace="ecommerce", app="ecommerce"}` |
| Recovery after spike | Metrics flatten when script ends (phase 3 hard stop) |

## Correlate a failed request

1. Find a slow/timeout line in script output:

   ```
   timestamp=... correlationId=... status=timeout responseTime=3000ms error=timeout
   ```

2. In Grafana **Explore → Loki**:

   ```logql
   {namespace="ecommerce", app="ecommerce"} |= "<correlationId>"
   ```

3. In **observability-debug-agent** Swagger (`http://localhost:8092/docs`):

   ```json
   { "query": "Investigate correlation id <correlationId> for ecommerce errors" }
   ```

## How the script creates pressure

- **Sudden spike**: Phase 2 jumps immediately from 5 rps to 400 rps (no ramp).
- **Concurrency**: Up to 500 in-flight requests with pooled connections queue at the JVM.
- **Timeouts**: 3s total / 1s connect / 2s read — clients fail under backlog while server threads stay busy.
- **Headers**: Each request sends `X-Request-ID`, `X-Correlation-Id`, `X-User-ID`, `X-Product-ID` for tracing.
