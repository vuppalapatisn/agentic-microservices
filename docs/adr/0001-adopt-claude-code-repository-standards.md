# ADR 0001: Adopt Claude Code repository standards and documentation

- **Status:** Accepted
- **Date:** 2026-07-05
- **Deciders:** Repository maintainers
- **Affected units/contracts:** None (documentation and agent-configuration only; no code or
  runtime contract changes)

## Context
The repository is a polyglot microservices + agentic-AI platform (four Java/Spring Boot services,
a Python FastAPI/LangGraph agent, a React UI, an observability stack, and two deployment paths).
It had no top-level guidance for AI agents or new engineers, no explicit rules, no reusable
skills, and no recorded governance. Onboarding and safe automated changes were slow and risky,
and there was no mechanism to preserve architectural intent over time.

## Decision
Introduce a Claude Code compatibility and documentation layer, entirely additive:

- `CLAUDE.md` — project overview, tech stack, structure, build/test/dev/deploy commands, coding
  standards (SOLID/Clean Code/DRY/KISS/YAGNI/Secure-by-Default), agent guidance, and forbidden changes.
- `.claude/rules/rules.md` — enforceable development, security, testing, CI/CD, and PR rules.
- `.claude/skills/{architecture-review,code-review,feature-development,troubleshooting,devops,security}/SKILL.md`
  — reusable task playbooks discoverable by Claude Code.
- `docs/GOVERNANCE.md` + `docs/adr/` — SemVer, ADR process, dependency upgrades, breaking-change,
  deprecation, and migration strategy, with an ADR template.

## Consequences
- **Positive:** faster, safer onboarding for engineers and AI agents; explicit contracts and
  guardrails; recorded intent that survives team/tooling turnover; a repeatable path for future
  decisions. SemVer impact: none (no versioned unit changes).
- **Negative / obligations:** the docs must be kept in sync with behavior changes (now a PR rule);
  contributors must learn the ADR + skill workflow. These files describe the codebase as of this
  date — any agent must verify a referenced file/flag still exists before relying on it.

## Alternatives Considered
- **Do nothing** — rejected: leaves onboarding and automated changes error-prone and intent undocumented.
- **A single README section instead of structured files** — rejected: not discoverable by Claude
  Code's skill/rules conventions and too coarse to enforce per-task guidance.
- **Generating code/CI changes now** — rejected as out of scope: this ADR intentionally makes no
  runtime change; functional improvements go through the normal feature/ADR process.
```