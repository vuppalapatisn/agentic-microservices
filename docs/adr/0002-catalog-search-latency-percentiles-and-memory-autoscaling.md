# ADR 0002: Catalog search, latency percentiles, and memory-based autoscaling

- **Status:** Accepted
- **Date:** 2026-07-13
- **SemVer impact:** MINOR (additive — new endpoints, a new MCP tool, new optional entity fields; no existing contract changed)

## Context and Problem
The demo needed a richer, Amazon-style product catalog with keyword search, and the observability
story needed to show request-latency percentiles (P90/P95/P99) and memory-driven horizontal
autoscaling under load. None of these existed: the catalog was 7 rows with 4 columns, there was no
search, no latency-histogram capability (Micrometer emitted no buckets and Prometheus dropped them
at scrape), and no HorizontalPodAutoscaler anywhere. Contracts on the path: the product/ecommerce
REST payloads, the observability-server MCP tool + REST surface, and the Prometheus scrape config.

## Decision
Additive changes only, using the existing seams:

1. **Catalog enrichment** — add `category`, `brand`, `stockQuantity`, `rating` to the product entity
   and `schema.sql`, expand seed data to ~36 products across categories, and give every product a
   matching image row. Mirror the new fields on the ecommerce aggregator's `Product` (which already
   ignores unknown JSON), so they flow through unchanged. Existing columns and the `/products`
   payload are untouched.
2. **Search** — add `ProductRepository.search(q, category)` (JPQL), a `ProductService` holding the
   validation, `GET /product-service/products/search`, and `GET /ecommerce-service/ecommerceProducts/search`
   on the aggregator so a search fans out product → images and carries the correlation id across
   services.
3. **Latency percentiles** — enable Micrometer `distribution.percentiles-histogram` for
   `http.server.requests` in the three business services, add the histogram buckets/sum to the
   Prometheus scrape keep-list, and expose `histogram_quantile()` through a new
   `get_latency_percentiles` MCP `@Tool` + `/metrics/latency-percentiles/{service}` REST endpoint.
   The LangGraph agent fetches these in a new `fetch_latency_metrics_node` on the monitoring path
   and folds P90/P95/P99 into the correlation evidence and the single LLM payload.
4. **Memory autoscaling** — add `autoscaling/v2` HPAs for product/images/ecommerce keyed on memory
   `AverageValue` (350Mi), min 1 / max 4 replicas.

## Consequences
- Positive: realistic search-driven load; end-to-end P90/P95/P99 latency analysis; load-reactive
  autoscaling — a complete metrics + logs demo for the observability-debug-agent.
- Negative / obligations:
  - Keeping the histogram buckets raises Prometheus cardinality (per URI × status × method × `le`).
    Acceptable at demo scale; revisit if the catalog/endpoints grow.
  - HPAs are inert until a **metrics-server** is installed (Docker Desktop needs
    `--kubelet-insecure-tls`). Documented in DEV-Readme; not auto-installed by `start.bat`.
  - `price` column widened to `DECIMAL(10,2)` to preserve cents (was scale-0 `DECIMAL`). Same JSON
    type (number), so no contract break.
  - Tests added (product search service/controller, Prometheus latency-query builder, agent
    classification + correlation); docs updated (CLAUDE.md, DEV-Readme, README, demo-usecases).

## Options Considered
- **Chosen: Micrometer histogram + `histogram_quantile` in Prometheus** — reuses the existing
  metrics pipeline; percentiles are computed server-side and aggregatable across pods.
- **Rejected: client-side percentiles via Micrometer `publishPercentiles`** — per-instance
  percentiles cannot be correctly aggregated across replicas (which HPA now creates), so tail
  latency across the fleet would be wrong.
- **Chosen: memory `AverageValue` HPA target** — the JVM's idle RSS sits near the 256Mi request, so
  a `Utilization` target would pin replicas at max; an absolute value scales only on real growth.
- **Rejected: CPU-based HPA** — the user asked specifically for memory-based autoscaling, and the
  JVM workload here is memory- rather than CPU-bound under the search load.
