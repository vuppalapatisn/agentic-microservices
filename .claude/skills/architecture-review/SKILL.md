---
name: architecture-review
description: >-
  Analyze architecture, run design reviews, and produce ADRs for the
  agentic-microservices platform. Use when evaluating a proposed design, assessing
  a new service/dependency, reviewing how a change fits the microservice + MCP +
  LangGraph topology, or recording an architecture decision.
---

# Architecture Review

## Description
Structured architecture analysis and design review for this polyglot microservices +
agentic-AI repository. Produces a clear assessment of how a change fits the existing
topology (five services across the `ecommerce` and `observability` namespaces, the Spring AI
MCP server, and the LangGraph agent) and, when a decision is made, an Architecture Decision
Record (ADR).

## Scope
- **In scope:** service boundaries and responsibilities, contracts (REST/MCP/env/K8s),
  data flow, coupling and cohesion, cross-cutting concerns (correlation-id, logging,
  observability), technology fit, ADR authoring.
- **Out of scope:** line-level bug hunting (use `code-review`), implementing the change
  (use `feature-development`), incident diagnosis (use `troubleshooting`).

## Inputs
- The proposed change or question (feature idea, new dependency/service, refactor).
- Relevant source: `microservices/*/`, `k8s/**`, `.github/workflows/**`, `CLAUDE.md`.
- Existing ADRs in `docs/adr/` and governance in `docs/GOVERNANCE.md`.

## Outputs
- A design assessment: fit with current architecture, contracts affected, coupling impact,
  alternatives, risks, and a recommendation.
- When a decision is reached: an ADR file `docs/adr/NNNN-title.md` (see the template).
- If contracts break: a pointer to the required deprecation/migration path.

## Process
1. **Map the current state.** Identify which of the five services and which namespace the
   change touches; trace the request/data flow (Chat UI → agent → MCP server → Prometheus/Loki
   → OpenAI). Note every contract on the path.
2. **Frame the decision.** State the problem, constraints, and the forces (latency, cost of
   LLM calls, deploy independence, security posture).
3. **Evaluate options** (at least two). Score each on: contract impact, coupling, complexity
   (KISS/YAGNI), security-by-default, testability, and 3-year maintainability.
4. **Check the seams.** Confirm the change uses an existing extension point (new graph node +
   client fetch + MCP tool; new observability query in service+client) rather than a new pattern.
5. **Recommend** and, if decided, **write the ADR** with status, context, decision, consequences.
6. **Flag follow-ups:** docs to update (`CLAUDE.md`, `architecture-diagram.md`), tests needed,
   deprecations to schedule.

## Best Practices
- Preserve service independence and the single-responsibility layering (controller/route →
  service/engine → client → DTO/schema).
- Keep the LLM call single, late, and fed a compact payload; keep correlation heuristics cheap.
- Prefer additive, contract-preserving designs. Reuse cross-cutting components.
- Record *why* (forces + trade-offs), not just *what* — future agents read the ADR to avoid
  re-litigating settled decisions.

## Anti-Patterns
- Adding a new microservice, datastore, queue, or framework speculatively (YAGNI).
- Coupling services through shared mutable state instead of the HTTP/MCP contract.
- Widening a tool/endpoint's responsibility instead of adding a focused new one (breaks SRP/OCP).
- Deciding a breaking change without an ADR or a migration plan.
- Designs that require secrets in code or containers running as root.

## Examples
- *"Add trace-based investigation (Tempo)."* → Assess as a new observability data source:
  new client + `ObservabilityService` method + MCP `@Tool` + agent graph node + fetch. Contract-
  additive. Write ADR covering the new dependency, its K8s footprint, and cost/latency impact.
- *"Should the agent call Prometheus directly instead of via observability-server?"* → Evaluate
  coupling vs. the MCP indirection; likely reject (breaks the MCP-tool abstraction and DIP).
  Record the rejection as an ADR so it isn't revisited.