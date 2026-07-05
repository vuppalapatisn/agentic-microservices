# Repository Rules

Rules every Claude Code agent (and human contributor) **must** follow in this repository.
These are enforceable expectations, not suggestions. When a rule and a user request
conflict, surface the conflict and follow the safer path; do not silently violate a rule.

Companion: [/CLAUDE.md](../../CLAUDE.md) (project context, commands, standards) and
[docs/GOVERNANCE.md](../../docs/GOVERNANCE.md) (versioning, deprecation, migration).

---

## 1. Development Rules

### Backwards compatibility
- **Public contracts are frozen unless a breaking change is explicitly approved via an ADR.**
  Contracts include: REST paths and payloads, MCP tool names + parameter names/types,
  Kubernetes service names/ports, environment-variable names, and the `X-Correlation-Id` header.
- Prefer **additive** changes: new optional fields, new endpoints, new tools, new graph nodes.
- If you must break a contract, follow the deprecation → migration path in `docs/GOVERNANCE.md`.
- Keep each of the five services independently buildable and deployable. A change to one
  service must not require lockstep redeploy of another unless the contract genuinely changed.

### Versioning
- Follow **Semantic Versioning** (`MAJOR.MINOR.PATCH`) — see `docs/GOVERNANCE.md`.
- Java service versions live in each `pom.xml`; the agent version is in `app/main.py`
  (`FastAPI(version=...)`). Bump the version when behavior changes; MAJOR only for breaking contracts.
- Docker image tags: reproducible deploys use immutable tags (timestamp/SHA/run-number).
  `latest` is for local convenience only.

### Code review requirements
- Every change is reviewed against the `code-review` skill before merge (see PR Rules below).
- No self-merge of non-trivial changes without a passing review pass.
- Scope discipline: no drive-by refactors mixed into a feature/fix PR.

### Documentation updates
Any change that affects behavior **must** update docs in the same PR:
- New/changed command or flow → `README.md` and/or `DEV-Readme.md`.
- New/changed contract, layering, or dependency → `CLAUDE.md`.
- An architectural decision → a new ADR in `docs/adr/`.
- UI change → `chatbot-ui-readme.md`. Architecture change → `architecture-diagram.md`.

---

## 2. Security Rules

### Secrets handling
- **Never commit secrets** (API keys, tokens, passwords, kubeconfigs) to code, YAML,
  Dockerfiles, `.env`, or logs. `k8s/observability-debug-agent/secret-example.yaml` is a
  template — it must never contain a real key.
- Secrets are supplied only via **Kubernetes secrets** (runtime) and **GitHub Actions secrets** (CI):
  `OPENAI_API_KEY`, `DOCKER_USERNAME`, `DOCKER_PASSWORD`, `IBM_CLOUD_API_KEY`,
  and the `dockerhub-registry-secret` pull secret.
- Read secrets through the config layer (`Settings` / env vars / Spring properties), never inline.
- Never log secrets, tokens, request bodies, or PII. Structured logs carry event names +
  safe metadata (service, correlationId, counts, durations) only.

### Encryption / transport
- All external calls (OpenAI, registries, IBM Cloud) use TLS/HTTPS. The agent image installs
  CA certificates deliberately — do not remove that step.
- Do not disable certificate verification or add `--insecure`/`verify=False` to reach a service.

### Authentication standards
- Do not weaken the CORS allow-list on the agent; never combine `allow_origins=["*"]` with
  `allow_credentials=True`.
- Any new externally-exposed endpoint must state its auth posture explicitly (this is a demo
  stack; new privileged surfaces require an ADR before exposure).
- Container security: images run as a **non-root** user — keep it. No `privileged`,
  `hostNetwork`, `hostPath`, or added Linux capabilities without an ADR.

---

## 3. Testing Rules

### Unit testing
- Every bug fix ships with a regression test that fails before the fix and passes after.
- New pure logic must be unit-tested with no network: Python `CorrelationEngine`/`util`
  functions directly; Java client/service parsing and validation with mocked HTTP.
- **Tests must never call OpenAI or any paid/external API.** Stub the LLM and stub
  `ObservabilityAgentClient`/HTTP clients.

### Integration testing
- Changes to a contract (REST/MCP) require at least a request→response assertion at the
  boundary (Spring Boot Test / FastAPI `TestClient`) covering the happy path and one failure path
  (e.g. invalid input → 400, upstream failure → 503).
- Runtime-affecting changes must be verified end-to-end before "done": redeploy the single
  service, generate traffic, run `/api/v1/investigate`, confirm correlation-id flows and
  Grafana links resolve.

### Coverage expectations
- No merge may **reduce** existing coverage of the code it touches.
- New non-trivial logic targets meaningful coverage of branches (validation, routing decisions,
  correlation heuristics), not a headline percentage. Prefer tests that assert behavior over
  tests that assert implementation.

---

## 4. CI/CD Rules

### Pipeline validation
- Do not merge with a red pipeline. `docker_build.yaml` must build every affected service
  (Maven build + Docker build, multi-arch) on the PR.
- Changing a workflow requires validating it (dry-run/`workflow_dispatch`) and updating the
  header comments in the workflow file that document required secrets/prerequisites.

### Security scanning
- Keep dependencies **pinned** (Python `requirements.txt`, Maven versions, npm lockfile).
- Do not introduce a dependency without checking it is maintained and license-compatible;
  prefer the frameworks already present over adding new ones.
- If a dependency-scanning step exists or is added, its findings must be triaged before merge —
  never suppress a vulnerability finding without a documented reason.

### Build verification
- Java: `mvn clean package` must pass (tests included) for each touched service.
- Python: `python -m pytest` must pass; the agent image must build (which also builds the UI).
- Multi-arch builds (`amd64`+`arm64`) must remain intact — do not add arch-specific steps that
  break one platform.
- Reproducible deploys use immutable image tags; never deploy `latest` to a shared cluster.

---

## 5. PR Rules

### Mandatory checks (all must be green before merge)
1. CI build passes for every affected service (Java build/tests, Python tests, UI build).
2. No secrets added (scan the diff for keys/tokens/passwords/kubeconfig).
3. Contracts preserved, or a breaking change is documented + versioned per governance + ADR.
4. Tests added/updated (regression test for fixes; boundary test for contract changes).
5. Docs updated in the same PR (see Documentation Updates).
6. Input validation and non-root/security posture preserved.
7. Change is scoped — no unrelated refactors, no speculative abstractions (YAGNI).

### Review process
- Run the `code-review` skill and address every finding (fix or justify).
- PR description states: what changed, why, which services/contracts are affected, how it was
  verified (commands run, flows exercised), and any follow-ups.
- Prefer new commits over force-amending during review; never `--force` push to `master`/`main`.
- A human owner approves changes to security posture, contracts, CI/CD, or governance.
```