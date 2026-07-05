# Repository Governance

How this repository evolves safely over the long term (3+ years). It defines versioning,
architecture decisions, dependency upgrades, breaking-change management, deprecation, and
migration. It is technology-agnostic where possible so it survives stack changes.

Companions: [/CLAUDE.md](../CLAUDE.md), [.claude/rules/rules.md](../.claude/rules/rules.md),
ADRs in [docs/adr/](adr/).

---

## 1. Semantic Versioning

Each independently-deployable unit is versioned with **SemVer** — `MAJOR.MINOR.PATCH`:

- **MAJOR** — a breaking change to a public contract (REST path/payload, MCP tool name/params,
  K8s service name/port, env-var name, `X-Correlation-Id` behavior). Requires an ADR and a
  migration path.
- **MINOR** — backwards-compatible new capability (new endpoint, tool, graph node, optional field).
- **PATCH** — backwards-compatible bug fix or internal change.

**Where versions live:** Java services in each `pom.xml` (`<version>`); the Python agent in
`app/main.py` (`FastAPI(version=...)`); the UI in `ui/package.json`. Bump the version in the
same PR as the behavior change.

**Image tags:** shared/remote deploys use immutable tags (timestamp / commit SHA / CI run number,
as the workflows already produce). `latest` is for local convenience only and must never be the
deployed tag on a shared cluster.

---

## 2. Architecture Decision Records (ADRs)

Every architecturally-significant decision is recorded as an ADR so future engineers and agents
understand *why*, not just *what*, and don't re-litigate settled choices.

**When to write one:** adding/removing a service or dependency; changing a contract; changing the
data flow, the LLM boundary, security posture, or the deployment topology; rejecting a tempting
option (record the rejection too).

**Process:**
1. Copy `docs/adr/0000-template.md` to `docs/adr/NNNN-short-title.md` (next number).
2. Fill in Status, Context (forces/constraints), Decision, Consequences (including negatives),
   and Alternatives considered.
3. Status lifecycle: `Proposed` → `Accepted` → (later) `Deprecated` / `Superseded by NNNN`.
4. Reference the ADR from the PR and from `CLAUDE.md` if it changes documented behavior.

ADRs are immutable once Accepted — supersede with a new ADR rather than editing history.

---

## 3. Dependency Upgrades

- **Pin everything:** Python `requirements.txt` exact versions, Maven versions (managed via the
  Spring Boot parent / Spring AI BOM where possible), npm `package-lock.json`.
- **Cadence:** review dependencies regularly; apply security patches promptly (PATCH/MINOR),
  batch routine upgrades.
- **Framework majors** (Java, Spring Boot, Spring AI, LangGraph, FastAPI, React) require an ADR —
  they carry migration cost and contract risk.
- **Per upgrade:** read the changelog for breaking/security notes; confirm license compatibility;
  build + test the affected service; verify multi-arch images still build; run the affected flow
  end-to-end. One dependency concern per PR where practical.
- **Never** float to `latest`, downgrade Java below 21, or add an unmaintained dependency.

---

## 4. Breaking Change Management

A change is **breaking** if it alters any public contract listed in SemVer §1. To make one:

1. **Justify** it in an ADR (why additive won't work).
2. **Version** it as MAJOR on the affected unit.
3. **Provide a migration path** (§6) and, where feasible, a deprecation window (§5) with the old
   and new behavior coexisting.
4. **Update every consumer** in the repo (agent ↔ MCP server ↔ services ↔ UI ↔ manifests ↔ scripts
   ↔ workflows) in a coordinated way, and update all docs.
5. **Announce** in the PR and the changelog/release notes.

Silent breaking changes are prohibited (see `.claude/rules/rules.md`).

---

## 5. Deprecation Strategy

- **Mark** the deprecated element clearly: `@Deprecated` (Java, with `@deprecated` Javadoc and the
  replacement), a deprecation note in Pydantic/FastAPI (docstring + response header/log warning),
  and a `DEPRECATED:` comment pointing to the replacement and the removal target.
- **Keep it working** through at least one MINOR release (a deprecation window); the replacement
  must exist and be documented before the old path is deprecated.
- **Log** deprecated usage (structured warning) so real usage is visible before removal.
- **Remove** only in a MAJOR release, after the window, with the removal noted in release notes.
- **Document** deprecations in the relevant README/`CLAUDE.md` and reference the ADR.

---

## 6. Migration Strategy

For any breaking change or deprecation removal, ship a migration that is safe and reversible:

- **Expand → migrate → contract:** add the new contract alongside the old (expand), move consumers
  over (migrate), then remove the old one in a later MAJOR (contract). This keeps deploys independent.
- **Data/schema:** the H2 seed files (`schema.sql`/`data.sql`) are recreated per deploy; for any
  future persistent store, use forward-only, backwards-compatible migrations with a rollback plan.
- **Rollout:** deploy one service at a time; rely on `kubectl rollout status`; keep the previous
  immutable image tag available for fast rollback.
- **Migration guide:** for a MAJOR, write a short "migrating from vX to vY" section (what changed,
  what consumers must do, timeline) in the release notes / README.
- **Verify:** exercise both old and new paths during the window; confirm correlation-id and logging
  still flow; roll back by redeploying the prior image tag if verification fails.

---

## 7. Forward Compatibility Principles

To stay maintainable and low-debt over 3+ years:

- Keep contracts additive and services independently deployable.
- Keep cross-cutting concerns (correlation-id, structured logging, Grafana links, config-via-env)
  centralized and reused, not duplicated.
- Keep the LLM interaction single, late, compact, and model-configurable (`OPENAI_MODEL`) so the
  provider/model can evolve without reworking the graph.
- Prefer standard, well-supported building blocks already in the stack over novel ones (KISS/YAGNI).
- Record decisions as ADRs so intent survives team and tooling turnover.