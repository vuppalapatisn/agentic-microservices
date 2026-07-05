# AGENTS.md — AI Agent Operating Procedures

Operating procedures for **any** AI coding agent (Claude Code and others) working in this
repository. This is the cross-tool standard file; Claude Code also reads [CLAUDE.md](CLAUDE.md)
for project detail. When guidance here and in `.claude/rules/rules.md` conflict, the **rules win**
— they are enforceable; this file is procedure.

> **Prime directive:** the platform is already built. **Do not redesign it.** Make the smallest,
> contract-preserving change that satisfies the request, prove it, and document it.

---

## 0. Ground truth (verify before you trust)

These files describe the repo as of their writing. Before relying on any file, flag, or command a
doc mentions, **confirm it still exists** in the working tree. If reality and the docs disagree,
trust the code and surface the discrepancy — do not silently "fix" the code to match a doc.

Read, in order: this file → [CLAUDE.md](CLAUDE.md) → [.claude/rules/rules.md](.claude/rules/rules.md)
→ the specific service source → the relevant skill in [.claude/skills/](.claude/skills/).

---

## 1. Operating loop

For every task, follow this loop:

1. **Understand.** Restate the goal. Identify the target service(s) and every contract on the path
   (REST/MCP/env/K8s Service+port/`X-Correlation-Id`).
2. **Load the right skill.** Match the task to a skill and follow its Process:
   - Java service change → `spring-boot-architecture`
   - MCP tool/client → `mcp-development`
   - LangGraph flow/reasoning → `agent-orchestration`
   - metrics/logs/dashboards/tracing → `observability`
   - K8s manifests/rollouts → `eks-kubernetes`
   - AWS ECS/CloudFormation → `aws-platform-engineering`
   - CI/CD pipelines → `azure-devops-pipelines` (GitHub Actions is the active runner)
   - new feature end-to-end → `feature-development`
   - reviewing a diff → `code-review`; security gate → `security-review`; broad security → `security`
   - incident/debugging → `troubleshooting`; perf/cost → `performance-engineering`
   - design decision/ADR → `architecture-review`
3. **Plan the smallest change.** Prefer additive, single-service edits. If a contract must change,
   **stop** and route through `architecture-review` + `docs/GOVERNANCE.md` (ADR + SemVer + migration).
4. **Implement** to match the file's existing conventions (see CLAUDE.md Coding Standards).
5. **Test** (regression for fixes, boundary for contract changes; stub the LLM and network).
6. **Verify end-to-end** for runtime changes: build/test the service, redeploy it
   (`restart--redeploy-service.*`), generate traffic, exercise the flow, confirm correlation-id +
   Grafana links still work.
7. **Document** in the same change (CLAUDE.md / READMEs / ADR as applicable).
8. **Report** honestly: what changed, why, contracts affected, commands run, results (including
   failures), and follow-ups. Never claim "done/verified" for a step you didn't run.

---

## 2. Guardrails (hard limits)

- **Never** commit secrets or print them to logs. Runtime secrets come from K8s/CI secret stores.
- **Never** silently change a contract (REST path, MCP tool name/param, K8s Service/port, env var).
- **Never** run containers as root, add `privileged`/`hostNetwork`/`hostPath`, or disable TLS/cert
  verification.
- **Never** widen CORS to `*` with credentials, or weaken input validation.
- **Never** call OpenAI / paid APIs / the network from tests.
- **Never** add a second LLM call or dump raw logs/metrics into the prompt — keep it single, late, compact.
- **Never** add a service/datastore/queue/framework speculatively (YAGNI), unpin a dependency, or
  downgrade Java below 21 / change a framework major, without an ADR.
- **Never** `--force` push to `master`/`main`, skip CI, or self-merge changes to security, contracts,
  CI/CD, or governance without a human owner's approval.

See the full list in [CLAUDE.md](CLAUDE.md) → "Forbidden Changes".

---

## 3. When to ask a human vs. proceed

- **Proceed** (with sensible defaults, noting them) for: additive single-service changes, tests,
  docs, bug fixes with a regression test, and anything squarely inside a skill's Process.
- **Ask / open an ADR first** for: any contract change, a new dependency/service, security-posture
  or auth changes, CI/CD or governance changes, or anything the guardrails forbid by default.
- If a request conflicts with a rule, **surface the conflict** and propose the compliant path rather
  than quietly violating the rule.

---

## 4. Multi-agent & concurrency etiquette

- State your scope up front (files/services you will touch) so parallel agents don't collide.
- Keep changes isolated to one service/seam per task; don't refactor shared cross-cutting code
  (correlation filter, JSON logger, Grafana link builder) as a side effect — that has repo-wide blast radius.
- Leave the tree building and tests green. If you must pause mid-task, report exact state and next step.
- Prefer additive commits over rewriting another agent's/developer's in-flight work.

---

## 5. Definition of done

A change is done only when: it satisfies the request; contracts are preserved (or properly
versioned + migrated); tests pass and cover the change; the affected service builds and (for
runtime changes) was exercised end-to-end; docs are updated; and the guardrails in §2 hold.
State this explicitly in your final report.