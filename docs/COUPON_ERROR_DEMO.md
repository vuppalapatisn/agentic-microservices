# Coupon error demo

Demonstrates **logs-only** investigation when ecommerce calls a fictitious `coupon-service` that is not deployed in the cluster.

## 1. Trigger the error

```powershell
curl -i -X POST http://localhost:8090/ecommerce-service/apply-coupon `
  -H "Content-Type: text/plain" `
  -d "DISC20"
```

- Expect a non-2xx response (502 Bad Gateway).
- Copy `X-Correlation-Id` from the response headers.

Ecommerce logs an ERROR with stack trace, for example:

```text
coupon_apply_failed couponCode=DISC20 targetUrl=http://coupon-service:8090/coupons/DISC20
```

## 2. Verify in Loki (optional)

In Grafana Explore (Loki):

```logql
{namespace="ecommerce"} |= "<your-correlation-id>"
```

You should see ERROR lines mentioning `coupon-service` or connection failure.

## 3. Investigate in chat

In the talk-to-observability chat UI (or `POST /api/v1/investigate`):

- `Give me details of error for request <correlation-id>`
- `Give me stack trace of error for request <correlation-id>`

**Expected routing:** `needs_logs=Y`, `needs_monitoring=N` — no heap, threads, or request-rate evidence; Grafana Explore link only (no metrics dashboard link).

## 4. Redeploy after code changes

```bat
restart--redeploy-service.bat ecommerce talk-to-observability-agent
```

Build Java services with Maven before redeploying ecommerce and observability-agent.
