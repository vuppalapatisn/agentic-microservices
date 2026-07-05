---
name: troubleshooting
description: >-
  Diagnose production and local issues in the agentic-microservices platform —
  debugging failures, tracing requests by correlation-id, and root-cause analysis
  across the services, observability stack, and the agent itself. Use for
  ImagePullBackOff, 503s from /investigate, empty Loki/Grafana, or agent errors.
---

# Troubleshooting

## Description
A disciplined debugging and root-cause-analysis workflow for this platform. It uses the same
signals the product itself uses — correlation-ids, Loki logs, Prometheus metrics, Grafana —
plus Kubernetes state, to isolate failures fast.

## Scope
- **In scope:** runtime failures (pods, rollouts, image pulls), request failures (5xx/503),
  agent investigation errors, missing logs/metrics, correlation-id tracing, RCA writeups.
- **Out of scope:** implementing the fix (hand off to `feature-development`/`code-review`),
  pipeline redesign (`devops`).

## Inputs
- Symptom + when it started; the affected service/namespace; a correlation-id if available.
- Access to `kubectl`, Grafana (`:3000`), Prometheus (`:9090`), and the agent UI (`:8092`).

## Outputs
- An isolated root cause with evidence (log lines, metric points, pod events).
- A minimal, safe remediation (or a clear handoff), and an RCA note capturing cause + prevention.

## Process
1. **Reproduce & scope.** Confirm the symptom; identify service + namespace
   (`ecommerce` vs `observability`). Grab or generate a correlation-id
   (`scripts/simulate_traffic_spike.py` prints one per request).
2. **Check platform state:**
   ```bash
   kubectl get pods -n ecommerce
   kubectl get pods -n observability
   kubectl describe pod -l app=<svc> -n <ns> | tail -25   # events: ImagePullBackOff, OOMKilled
   kubectl logs -n <ns> deploy/<svc> -f
   ```
3. **Trace the request** across services in Loki:
   ```logql
   {namespace="ecommerce"} |= "<correlation-id>"
   ```
   Widen the time range if empty; confirm Promtail is shipping (redeploy if log paths changed).
4. **Correlate metrics** in Prometheus/Grafana (heap used vs max, request rate, threads) for
   the incident window.
5. **Localize:** app bug vs. dependency (product/images) vs. observability data plane
   (Prometheus/Loki/Promtail) vs. the agent (missing `OPENAI_API_KEY`, upstream 503).
6. **Root cause** with evidence; propose the smallest safe fix; write a short RCA (see `result.md`
   for the existing format) capturing trigger, cause, fix, and prevention.

## Known failure signatures
- **ImagePullBackOff** → missing/incorrect `dockerhub-registry-secret` in the namespace
  (see `k8s/dockerhub-secret.yaml`), or a bad image tag.
- **503 on `/api/v1/investigate`** (pod UP) → missing `OPENAI_API_KEY` secret, or
  observability-server/Loki/Prometheus error. Read the response `detail` and `X-Correlation-Id`.
- **Empty Loki/Grafana** → time range too narrow, no traffic yet, or Promtail path mismatch
  (`ecommerce_ecommerce-*`, dash not underscore) — reapply promtail configmap + rollout restart.
- **kubectl context lost between CI steps** → re-run `ibmcloud ks cluster config` in the step.

## Best Practices
- Follow the evidence; change one variable at a time; note what you ruled out.
- Use the correlation-id as the thread through every service's logs.
- Prefer read-only diagnosis first; make changes only once the cause is isolated.

## Anti-Patterns
- Restarting/redeploying blindly to "see if it helps" before reading pod events and logs.
- Editing code before the failure is localized to a service.
- `kubectl delete`/`--force` on shared resources during diagnosis.
- Diagnosing without a correlation-id when one is available.

## Examples
- *"/investigate returns 503 but the pod is Running."* → check response `detail` + header, then
  `kubectl logs deploy/observability-debug-agent`; find missing `OPENAI_API_KEY` → create the
  secret; re-run; confirm 200 and a summary with Grafana links.
- *"Grafana shows no logs after a traffic run."* → widen to Last 6h; if still empty, reapply
  `k8s/observability/promtail/configmap.yaml` and `kubectl rollout restart ds/promtail`.