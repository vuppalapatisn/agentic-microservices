---
name: code-review
description: >-
  Review pull requests and diffs in the agentic-microservices repo for correctness,
  security, and performance. Use when reviewing a PR, a working diff, or before
  committing non-trivial changes across the Java services, the Python LangGraph agent,
  or the React UI.
---

# Code Review

## Description
A repeatable review pass covering correctness, security, and performance for this repo's
Java (Spring Boot / Spring AI), Python (FastAPI / LangGraph), and TypeScript (React) code.
Enforces the contracts, conventions, and rules in `CLAUDE.md` and `.claude/rules/rules.md`.

## Scope
- **In scope:** diff correctness, contract preservation, security posture, performance of
  hot paths (LLM calls, metric/log fetches), test adequacy, docs sync.
- **Out of scope:** high-level design fit (use `architecture-review`), deep incident RCA
  (use `troubleshooting`).

## Inputs
- The diff / PR (branch or `git diff`), the changed services, and the PR description.
- `CLAUDE.md`, `.claude/rules/rules.md`, and the touched service's tests.

## Outputs
- A ranked list of findings (most severe first), each with file:line, why it matters, and a fix.
- A verdict: approve / request-changes, with the mandatory-checks status from the PR Rules.

## Process
1. **Contracts first.** Did REST paths, response schemas, MCP tool names/params, K8s
   service names/ports, or env-var names change? If so, is it approved + versioned + documented?
2. **Correctness.** Trace the changed path. For the agent: graph routing (`_route_after_*`),
   state keys, time-range parsing, correlation logic. For Java: validation, null/blank/range
   checks, DTO shaping, exception mapping. Confirm a regression/boundary test exists and fails
   before the fix.
3. **Security.** No secrets in diff; input validated; non-root preserved; CORS allow-list not
   widened; TLS/cert verification intact; no secrets/PII/request bodies logged; deps pinned.
4. **Performance.** LLM is called once, late, with a compact payload; no N+1 metric/log fetches;
   fetches are gated by the plan flags; no blocking calls added to async paths.
5. **Cross-cutting.** Correlation-id propagation and structured JSON logging present on new paths.
6. **Hygiene.** Scope is tight (no unrelated churn), naming/style match the file, docs updated.

## Best Practices
- Review by contract → correctness → security → performance → hygiene, in that order.
- Cite exact `file:line`; give the fix, not just the complaint. Rank by blast radius.
- Verify claims by reading the code path, not the PR description.
- Prefer requesting a small test over a long comment thread.

## Anti-Patterns (to catch and to avoid in review comments)
- Approving a silent breaking change to a contract.
- Field injection where constructor injection is the pattern; new global mutable state.
- Broadening a tool/endpoint's responsibility; duplicating the correlation filter, logger,
  or Grafana link builder instead of reusing them.
- Tests that hit OpenAI or the network; snapshot tests that assert implementation detail.
- Rubber-stamping without running the touched service's build/tests.

## Examples
- *New MCP tool added* → confirm `@Tool`/`@ToolParam` descriptions, input validation
  (blank service, inverted range, non-positive step), DTO mapping, and a client/service test.
- *New graph node* → confirm it's wired with the right conditional edge, sets only its own
  state keys, stubs the client in tests, and doesn't add a second LLM call.