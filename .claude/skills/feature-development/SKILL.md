---
name: feature-development
description: >-
  Implement new features in the agentic-microservices platform end-to-end — impact
  analysis, implementation across the right service, and testing strategy. Use when
  adding an investigation capability, an MCP tool, an observability query, an API
  endpoint, or a UI feature.
---

# Feature Development

## Description
Guides a new feature from request to verified change, staying inside the existing seams of
the five-service topology. Covers impact analysis, where the change belongs, how to implement
it consistently, and how to test it.

## Scope
- **In scope:** new investigation data/flows (agent), new MCP tools/observability queries
  (server), new API behavior (Java services), UI features (React), and their tests + docs.
- **Out of scope:** design decisions that change architecture (do `architecture-review` first),
  incident debugging (`troubleshooting`), pipeline changes (`devops`).

## Inputs
- The feature request and its acceptance criteria (ideally a use case from `demo-usecases.md`).
- `CLAUDE.md` (seams, conventions), the target service source, and its tests.

## Outputs
- The implemented feature within one service (or a contract-preserving addition across two).
- Tests (unit + boundary), updated docs, and a short verification note (commands run, flow driven).

## Process
1. **Impact analysis.** Identify the target service and whether any contract changes. Map the
   full path the feature touches (Chat UI → agent → MCP server → Prometheus/Loki → OpenAI).
   Prefer an **additive** change. If a contract must change, stop and route through
   `architecture-review` + governance.
2. **Pick the seam:**
   - New investigation signal → add a LangGraph node in `app/graph/workflow.py`, a fetch in
     `app/mcp/observability_client.py`, and a matching `@Tool` in `ObservabilityTools` +
     `ObservabilityService` + client method.
   - New observability query only → `ObservabilityService` + `PrometheusClient`/`LokiClient` (+ tool).
   - New API behavior → the service's controller/route; validate input; map errors.
   - UI → `ui/src/` components/api; keep the built bundle served by the agent image.
3. **Implement** to match conventions: SRP layering, constructor injection (Java), typed
   Pydantic/`TypedDict` state (Python), correlation-id + structured logging on every new path.
4. **Test** (see strategy below).
5. **Verify end-to-end:** build/test the service, redeploy it
   (`restart--redeploy-service.* <service>`), generate traffic, exercise the flow.
6. **Document:** update `CLAUDE.md`/`DEV-Readme.md`/`README.md`/`chatbot-ui-readme.md` as relevant.

## Testing Strategy
- **Unit:** pure logic without network — Python `CorrelationEngine`/`util`; Java client/service
  parsing + validation with mocked HTTP. New routing decisions get direct tests.
- **Boundary:** for any contract touch, add a request→response test (Spring Boot Test /
  FastAPI `TestClient`) for the happy path and one failure path.
- **Stub the LLM and the observability client** — never call OpenAI or the network in tests.
- Add a regression test for every bug found while building.

## Best Practices
- Smallest change that meets the criteria; reuse existing components; keep the LLM call single/late.
- Gate new fetches behind plan flags so unrelated investigations don't pay for them.
- Keep new tools/endpoints narrow and validated; keep the change within one service where possible.

## Anti-Patterns
- Adding a new service/datastore/framework for a feature an existing seam can serve (YAGNI).
- Threading feature-specific data through unrelated node return values or shared state.
- A second LLM call, or a fat payload, when the correlation engine can pre-digest the data.
- Skipping the end-to-end verification, or shipping without updating docs/tests.

## Examples
- *"Investigate GC pause time."* → new client method (Prometheus GC metric) → service method →
  `@Tool` → agent fetch node gated by a plan flag → correlation input → prompt field. Unit-test
  the parsing + correlation; boundary-test the tool; drive an investigate call.
- *"Show the correlation-id in the chat UI response."* → UI-only: extend `types.ts`, render in
  `AssistantMessage.tsx`; `npm run build`; no backend contract change.