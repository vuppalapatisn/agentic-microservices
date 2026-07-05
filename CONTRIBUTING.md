# Contributing

Contribution standards for **agentic-microservices**. Written for a team of multiple developers,
multiple AI agents, and CI/CD automation, optimized for long-term maintainability. It does not
change the architecture — it codifies how to work within it.

**Read first:** [CLAUDE.md](CLAUDE.md) (project map, commands, standards) ·
[.claude/rules/rules.md](.claude/rules/rules.md) (enforceable rules) ·
[docs/GOVERNANCE.md](docs/GOVERNANCE.md) (SemVer, ADRs, deprecation, migration) ·
[AGENTS.md](AGENTS.md) (AI agent operating procedures).

---

## Golden rules

1. **Preserve contracts.** REST paths/payloads, MCP tool names/params, K8s Service names/ports,
   env-var names, and `X-Correlation-Id` behavior are frozen unless changed via an ADR + SemVer bump.
2. **Stay in one service.** Each of the five services builds and deploys independently. Prefer
   additive, single-service changes; cross-service changes must keep the contract intact.
3. **No secrets, ever.** Not in code, YAML, Dockerfiles, `.env`, or logs. Runtime secrets come from
   Kubernetes secrets / CI secret stores only.
4. **Smallest change that works.** No speculative abstractions, services, or dependencies (YAGNI).
5. **Match the file you're editing.** Layering, naming, injection style, logging — consistency over preference.

---

## Repository layout (where things go)

| Area | Path | Skill to load |
|------|------|---------------|
| Java services | `microservices/{ecommerce,product,images,observability-server}` | `spring-boot-architecture` |
| MCP tools ↔ agent client | `observability-server/.../mcp/` ↔ `observability-debug-agent/app/mcp/` | `mcp-development` |
| LangGraph agent | `observability-debug-agent/app/graph/`, `correlation/`, `services/`, `prompts/` | `agent-orchestration` |
| Chat UI | `observability-debug-agent/ui/` | — |
| Metrics/logs/dashboards | service instrumentation, `k8s/observability/**` | `observability` |
| Kubernetes manifests | `k8s/**` | `eks-kubernetes` |
| AWS ECS/CloudFormation | `CF/**` | `aws-platform-engineering` |
| CI/CD | `.github/workflows/**` | `azure-devops-pipelines` (GitHub Actions is active) |
| Governance/decisions | `docs/GOVERNANCE.md`, `docs/adr/**` | `architecture-review` |

---

## Branching & commits

- Branch off `master`: `feature/<short-desc>`, `fix/<short-desc>`, `chore/<short-desc>`, `docs/<short-desc>`.
- **Never** commit directly to `master`; never `--force` push to `master`/`main`.
- Commit messages: imperative, scoped, explain *why* — e.g. `fix(agent): gate GC fetch behind plan flag`.
- One logical change per PR. No drive-by refactors mixed with a feature/fix.

---

## Local workflow

```bash
# 1. Build/test the service you touched
cd microservices/<service> && mvn test          # Java
cd microservices/observability-debug-agent && python -m pytest    # agent
cd microservices/observability-debug-agent/ui && npm run build    # UI

# 2. Redeploy just that service and verify end-to-end
./restart--redeploy-service.sh <service>         # or .bat on Windows
python scripts/simulate_traffic_spike.py         # generate traffic
# then exercise the affected flow (e.g. POST /api/v1/investigate) and check Grafana/Loki
```

Full stack: `./start.sh` / `start.bat`. Teardown: `./stop.sh` / `stop.bat`.
One-time: create the `OPENAI_API_KEY` secret (see [README.md](README.md)).

---

## Testing standards

- Every bug fix ships a **regression test** that fails before the fix.
- New logic is unit-tested with **no network**: Python `CorrelationEngine`/`util` directly; Java
  client/service parsing + validation with mocked HTTP (mirror `LokiClientTest`/`PrometheusClientTest`).
- Contract changes get a **boundary test** (Spring Boot Test / FastAPI `TestClient`): happy path + one failure path.
- **Never call OpenAI or the network in tests** — stub the LLM and `ObservabilityAgentClient`.
- Don't reduce existing coverage of code you touch. See Testing Rules in `.claude/rules/rules.md`.

---

## Documentation standards

Update docs in the **same PR** as the behavior change:
- Command/flow → `README.md` / `DEV-Readme.md`; UI → `chatbot-ui-readme.md`.
- Contract/layering/dependency → `CLAUDE.md`.
- Architectural decision → a new ADR (`docs/adr/`, use a template).
- Architecture picture → `architecture-diagram.md`.

---

## Pull request checklist (all must pass)

- [ ] CI green for every affected service (Java build/tests, agent tests, UI build, multi-arch image).
- [ ] No secrets in the diff.
- [ ] Contracts preserved — or breaking change has an ADR + SemVer bump + migration note + all consumers updated.
- [ ] Tests added/updated (regression for fixes; boundary for contract changes).
- [ ] Docs updated in this PR.
- [ ] Input validation + non-root/security posture preserved; CORS/allow-lists not widened.
- [ ] Scope is tight; no speculative abstractions.
- [ ] `code-review` and (if security-relevant) `security-review` skills run; findings fixed or justified.

---

## Review process

- Reviewers verify by reading the changed code path, not the PR description.
- A human owner must approve changes to **security posture, contracts, CI/CD, or governance**.
- Prefer new commits over force-amending during review.
- AI agents follow [AGENTS.md](AGENTS.md) and must state what they changed, why, and how they verified it.

---

## Dependencies & versioning

- Pin everything (Python `requirements.txt`, Maven versions via parent/BOM, npm lockfile).
- Framework major upgrades and any new dependency require an ADR (see governance).
- Follow SemVer; bump the affected unit's version in the same PR as the behavior change.
- Immutable image tags for shared deploys; `latest` is local-only.