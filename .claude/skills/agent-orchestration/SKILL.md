---
name: agent-orchestration
description: >-
  Extend the LangGraph investigation agent (observability-debug-agent) — graph nodes,
  routing, state, the correlation engine, prompts, and the single OpenAI reasoning call.
  Use when changing how the agent plans, fetches, correlates, or reasons over an investigation.
---

# Agent Orchestration

## Description
Governs the Python LangGraph agent that drives an investigation: parse → identify service →
identify time range → build plan → conditional fetches (logs/error-logs/heap/thread/request-rate)
→ correlation → LLM reasoning → response. Keeps the graph deterministic, cheap, and predictable.

**Reasoning:** the agent's value is a *bounded, explainable* investigation — a deterministic graph
that fetches only what the plan needs, correlates with cheap heuristics, and calls the LLM **once**
at the end with a compact payload. Preserving that shape keeps latency, cost, and behavior stable
as the agent grows.

## Scope
- **In scope:** `app/graph/workflow.py` (`StateGraph`, nodes, `_route_after_*`), `classification.py`
  (plan flags), `CorrelationEngine`, `ReasoningService`, `prompts/`, `models/schemas.py`, the
  `InvestigationState` TypedDict.
- **Out of scope:** MCP/tool contract (`mcp-development`), the observability data plane
  (`observability`), OpenAI SDK/model-selection specifics beyond wiring, infra.

## Inputs
- The behavior change requested; `workflow.py`, `classification.py`, `correlation/engine.py`,
  `services/reasoning_service.py`, `prompts/`, and `models/schemas.py`.

## Outputs
- New/updated graph node(s) wired via explicit edges, gated by plan flags, updating only their own
  state keys; correlation/prompt changes as needed; stubbed-LLM unit tests. One LLM call preserved.

## Process
1. **Classify first.** New data need → add a plan flag in `classification.py` and set it from the
   query so the fetch is gated (don't make every investigation pay for it).
2. **Add a node** in `workflow.py`: an `async def *_node(self, state) -> InvestigationState` that
   fetches via `ObservabilityAgentClient` and returns only its own keys. Register it and wire it
   with an explicit edge or a `_route_after_*` conditional.
3. **Correlate** in `CorrelationEngine` (pure, testable heuristics: `_latest`/`_peak`/`_average`) —
   keep new signal handling here, not in the LLM.
4. **Feed reasoning** by extending the compact `prompt_payload` and, if needed, a prompt builder in
   `prompts/`. Keep the single `ReasoningService.summarize(...)` call; add a `mode` rather than a
   second LLM call.
5. **Respond:** thread new outputs through `response_node`; keep Grafana deep-link building in `util`.
6. **Keep config env-driven** (`Settings`/`OPENAI_MODEL`); log via the JSON logger with correlationId.
7. **Test:** unit-test the engine and routing directly; stub `ObservabilityAgentClient` and the
   OpenAI call — never hit OpenAI or the network.

## Best Practices
- Deterministic routing; nodes are single-purpose and side-effect-free beyond their state keys.
- Gate fetches behind plan flags; keep the LLM call single, late, and compact.
- Keep `InvestigationState` keys typed and additive; keep response schema stable (a contract).
- Push logic into the correlation engine so behavior is testable without the LLM.

## Anti-Patterns
- A second/embedded LLM call, or a bloated prompt payload the engine could pre-digest.
- Fetching data unconditionally regardless of the plan; hidden cross-node coupling via shared state.
- Non-deterministic branching; time/randomness that makes runs irreproducible.
- Changing the `InvestigationResponse` shape (a contract) without versioning.

## Examples
- *Add GC-pause investigation* → plan flag `fetch_gc_pause` in `classification.py` → `fetch_gc_pause_node`
  gated by it → `CorrelationEngine` factors it into probable root cause → prompt gains a `gcPause`
  field under a `mode` → response unchanged in shape. Unit-test routing + engine with stubs.
- *Support "last N hours"* → extend `identify_time_range_node`'s regex only; no new node, no LLM change.
