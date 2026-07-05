---
name: observability
description: >-
  Work with the observability data plane — Prometheus metrics, Loki logs, Promtail,
  Grafana dashboards/deep-links, Micrometer instrumentation, and correlation-id
  tracing. Use when adding metrics/queries, dashboards, log labels, or tracing across services.
---

# Observability

## Description
Governs the signals the whole platform (and its AI agent) runs on: Micrometer/Prometheus metrics,
Loki logs shipped by Promtail, Grafana dashboards + deep-links, and the `X-Correlation-Id` thread
that stitches a request across services. Keeps signals consistent and queryable.

**Reasoning:** the agent can only diagnose what the platform emits. Consistent metric names, log
labels, and a correlation-id on every hop are prerequisites for correct investigations. Changes
here have outsized blast radius — a renamed label silently blinds the agent.

## Scope
- **In scope:** Micrometer/actuator instrumentation, PromQL/LogQL queries, `k8s/observability/**`
  (prometheus, loki, promtail, grafana), Grafana dashboards/datasources, correlation-id filters/
  middleware, JSON log structure, Grafana deep-link builders (`util/grafana_links.py`).
- **Out of scope:** how the agent reasons over signals (`agent-orchestration`), MCP tool wiring
  (`mcp-development`), cluster/infra mechanics (`eks-kubernetes`/`devops`).

## Inputs
- The signal/query/dashboard change; `k8s/observability/**`, the service's Micrometer config,
  `DEV-Readme.md` (queries), and `grafana_links.py` / `ObservabilityProperties`.

## Outputs
- Consistent new metric/log/dashboard/query, correlation-id preserved, deep-links still resolve;
  documented queries updated in `DEV-Readme.md`.

## Process
1. **Metrics:** expose via Micrometer/actuator with stable names (`http_server_requests_seconds_count`,
   `jvm_memory_used_bytes{area="heap"}`, etc.); confirm Prometheus scrapes the job label the agent uses.
2. **Logs:** keep structured JSON with `correlationId`; if adding a Loki label, update Promtail
   config and the LogQL the agent/DEV-Readme rely on (mind the `ecommerce_ecommerce-*` dash convention).
3. **Correlation-id:** every new inbound path reads/propagates `X-Correlation-Id` (Java filters /
   Python middleware) and logs it — no exceptions.
4. **Grafana:** add/adjust dashboards + datasource UIDs consistently; keep the dashboard/datasource
   UIDs that `Settings` (`GRAFANA_DASHBOARD_UID`, `GRAFANA_LOKI_DATASOURCE_UID`) reference.
5. **Deep-links:** if response links change, update `util/grafana_links.py`; verify the built URL opens.
6. **Verify end-to-end:** generate traffic, query Loki by correlation-id, confirm the metric appears
   in Prometheus/Grafana for the incident window.

## Best Practices
- Treat metric names, log labels, and datasource/dashboard UIDs as contracts the agent depends on.
- Keep cardinality sane; don't add high-cardinality labels (raw IDs) to metrics.
- Reuse the correlation filter/middleware and JSON logger; never log secrets/PII/request bodies.
- Keep query examples in `DEV-Readme.md` in sync with reality.

## Anti-Patterns
- Renaming a metric/label/UID the agent or dashboards use without updating both sides.
- Unstructured logs, or logs missing `correlationId`; high-cardinality metric labels.
- Removing actuator/Prometheus exposure from a service.
- Dashboards hand-edited in the UI but not captured back into `k8s/observability/grafana`.

## Examples
- *Add a GC dashboard panel* → confirm the GC metric is scraped → add the panel to the Grafana
  dashboard configmap → keep the dashboard UID → verify it renders for a traffic window.
- *Add a new service to tracing* → wire `CorrelationIdFilter`/middleware, ensure Promtail labels its
  logs, add its `job` to Prometheus scrape, confirm `{namespace=...} |= "<id>"` returns its lines.