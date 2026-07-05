---
name: performance-engineering
description: >-
  Analyze and improve performance/cost of the platform — the single LLM call and token
  cost, MCP/observability fetch fan-out, JVM heap/GC, HTTP timeouts, and investigation
  latency. Use when a change touches a hot path or when diagnosing slowness/cost.
---

# Performance Engineering

## Description
Keeps the platform fast and cost-aware without redesigning it. The dominant costs here are the
**OpenAI call** (latency + tokens) and the **observability fetch fan-out** (agent → MCP server →
Prometheus/Loki); secondary concerns are JVM heap/GC in the Java services and HTTP timeouts.

**Reasoning:** the agent's design already optimizes for this — one late LLM call with a compact
payload, and fetches gated by plan flags. Performance work here is mostly about *preserving* those
properties and measuring before changing, not adding caches/queues speculatively (YAGNI).

## Scope
- **In scope:** LLM token/payload size + call count, fetch fan-out and gating, PromQL/LogQL step +
  range sizing, `httpx`/RestClient timeouts, JVM heap/GC, investigation end-to-end latency, image
  build time.
- **Out of scope:** correctness (`code-review`), functional design (`architecture-review`), infra
  capacity/scaling policy (`eks-kubernetes`/`aws-platform-engineering`).

## Inputs
- The hot path or symptom; `workflow.py`/`classification.py` (fetch gating), `reasoning_service.py`
  + `prompts/` (payload), the clients' timeouts, `Settings` (`REQUEST_TIMEOUT_SECONDS`, `OPENAI_MODEL`),
  and Prometheus/Grafana for JVM/latency signals.

## Outputs
- A measured improvement (or a "no change — here's why") with before/after numbers: investigation
  latency, token count/cost, fetch count, or heap/GC; no new speculative machinery.

## Process
1. **Measure first.** Use the platform's own signals: `langgraph_run_complete` `durationMs` +
   `nodesExecuted`, per-request `durationMs` logs, Prometheus request-rate/heap/GC, Grafana panels.
   Establish a baseline before touching code.
2. **LLM cost:** confirm exactly one `ReasoningService.summarize` call; keep `prompt_payload`
   compact (pre-digest via `CorrelationEngine`, don't dump raw logs/metrics); pick the right
   `OPENAI_MODEL` for the task.
3. **Fetch fan-out:** ensure fetches are gated by plan flags so unrelated investigations don't run
   them; size PromQL `step`/range sensibly (avoid huge windows/tiny steps); avoid N+1 client calls.
4. **Timeouts:** keep `httpx`/RestClient timeouts bounded (`REQUEST_TIMEOUT_SECONDS`); fail fast on
   slow upstreams rather than hanging the investigation.
5. **JVM:** for the Java services, watch heap used vs max and GC; right-size container limits before
   reaching for code changes.
6. **Verify:** re-measure the same metrics; confirm the win and no correctness/behavior regression.

## Best Practices
- Profile before optimizing; change one thing; quantify the delta.
- Protect the "single late compact LLM call" and "gated fetch" invariants — they are the main levers.
- Bounded timeouts everywhere; keep step/range proportional to the window.
- Prefer reducing work (fewer tokens/fetches) over adding caches/queues.

## Anti-Patterns
- Optimizing without a baseline; micro-optimizing cold paths while the LLM call dominates.
- Adding a cache/queue/thread-pool speculatively (YAGNI) or a second LLM call.
- Dumping raw logs/metrics into the prompt; unbounded time ranges or timeouts.
- Raising heap limits to mask a leak instead of finding it (use `troubleshooting`).

## Examples
- *Investigations feel slow* → read `durationMs`/`nodesExecuted`; find an ungated heavy fetch or an
  oversized log payload in the prompt; gate it / trim it; re-measure the drop.
- *Token cost too high* → confirm the payload is engine-digested, not raw; shrink prompt fields;
  consider a smaller `OPENAI_MODEL` for simple modes; compare token counts before/after.