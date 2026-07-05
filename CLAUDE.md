# CLAUDE.md

Guidance for Claude Code (and any AI agent or engineer) working in this repository.
Read this file **before** making changes. It is the source of truth for how the
project is built, run, tested, and extended safely.

> Companion files:
> - Repository rules Claude must follow: [.claude/rules/rules.md](.claude/rules/rules.md)
> - Reusable skills: [.claude/skills/](.claude/skills/)
> - Governance (SemVer, ADRs, deprecation, migration): [docs/GOVERNANCE.md](docs/GOVERNANCE.md)
> - Architecture Decision Records: [docs/adr/](docs/adr/)

---

## Project Overview

### Purpose
`agentic-microservices` is a reference platform that demonstrates **automated
observability analysis with agentic AI**. It pairs a small e-commerce microservice
stack with an AI agent that diagnoses production issues — "Why did this request
respond slowly?", "What is the heap usage right now?", "What caused this API error?" —
by autonomously gathering logs and metrics and reasoning over them with an LLM.

### Business Goals
- **Reduce mean-time-to-resolution (MTTR)** for production incidents by automating the
  manual "check Grafana, correlate Loki logs, read stack traces" loop.
- **Show a working agentic pattern**: an MCP server that exposes observability data as
  tools, and a LangGraph agent that plans an investigation, fetches only the data it
  needs, correlates it, and produces a human-readable root-cause summary with deep links.
- **Serve as a teachable, extensible template** for building AI-assisted operations tooling
  on top of a standard Prometheus/Loki/Grafana stack.

### Architecture Summary
Two Kubernetes namespaces:

- **`ecommerce`** — the application under observation:
  - `ecommerce` — aggregator API; calls `product` and `images`; hosts the coupon endpoint.
  - `product` — product catalog (H2 in-memory, seeded by `schema.sql` + `data.sql`).
  - `images` — image metadata (H2 in-memory, seeded).
  - `ingress` — routes external traffic.
- **`observability`** — the data plane and the agentic layer:
  - `prometheus` (metrics), `loki` (logs), `promtail` (log shipper DaemonSet), `grafana` (dashboards).
  - `observability-server` — **Spring AI MCP server**; exposes logs/metrics as MCP `@Tool`s
    and REST endpoints, backed by `PrometheusClient` and `LokiClient`.
  - `observability-debug-agent` — **FastAPI + LangGraph + OpenAI** agent with a baked-in
    React chat UI; runs the investigation workflow and returns a root-cause summary.

Request flow for an investigation:
```
Chat UI ─▶ observability-debug-agent (LangGraph plan)
             ─▶ observability-server (MCP tools / REST)
                  ─▶ Prometheus (metrics) + Loki (logs)
             ◀─ correlated data
        ─▶ OpenAI (reasoning) ─▶ summary + Grafana deep links ─▶ Chat UI
```

Every request carries an `X-Correlation-Id` propagated across all services and emitted
in structured JSON logs, which is what lets the agent stitch a single request's story
together across services.

See [architecture-diagram.md](architecture-diagram.md) for the Mermaid diagram and
[demo-usecases.md](demo-usecases.md) for worked examples.

---

## Technology Stack

### Languages
- **Java 21** — the four Spring Boot services.
- **Python 3.12** — the LangGraph investigation agent.
- **TypeScript / React 18** — the chat UI (Vite build).
- **Bash / Batch** — local orchestration scripts (Linux/macOS `.sh`, Windows `.bat`).

### Frameworks & Key Libraries
| Area | Technology |
|------|-----------|
| Java services | Spring Boot 3.3.5 (apps) / 3.2.6 (observability-server) |
| MCP server | Spring AI 1.0.0 (`spring-ai-starter-mcp-server-webmvc`) |
| Metrics | Micrometer + `micrometer-registry-prometheus` |
| Java logging | `logstash-logback-encoder` (JSON logs) |
| API docs | springdoc-openapi (Swagger UI) |
| Agent API | FastAPI 0.115, Uvicorn |
| Agent orchestration | LangGraph 0.2.39 (`StateGraph`) |
| LLM | OpenAI SDK 1.54 (`gpt-4.1-mini` by default) |
| Agent models/validation | Pydantic 2.9 |
| HTTP client (agent) | httpx |
| UI | React 18 + Vite + TypeScript |

### Infrastructure
- **Docker** — multi-stage, multi-architecture images (`linux/amd64`, `linux/arm64`).
- **Kubernetes** — Docker Desktop (local) and IBM Cloud Kubernetes Service / IKS (remote).
- **Observability stack** — Prometheus, Loki, Promtail, Grafana (self-hosted manifests in `k8s/observability`).
- **CI/CD** — GitHub Actions (`.github/workflows`).
- **Registry** — Docker Hub.

---

## Repository Structure

```
.
├── microservices/                     # All service source (build contexts)
│   ├── ecommerce/                     # Java 21 / Spring Boot aggregator API
│   ├── product/                       # Java 21 / Spring Boot catalog (H2)
│   ├── images/                        # Java 21 / Spring Boot image metadata (H2)
│   ├── observability-server/          # Java 21 / Spring AI MCP server (Prometheus+Loki)
│   └── observability-debug-agent/     # Python FastAPI + LangGraph agent
│       ├── app/                       # Agent source (see below)
│       ├── tests/                     # pytest suite
│       ├── ui/                        # React + Vite + TS chat UI
│       └── Dockerfile                 # multi-stage: Node builds UI → Python runtime
├── k8s/                               # Kubernetes manifests
│   ├── ecommerce/ product/ images/    # app deployments/services/configmaps
│   ├── ingress/                       # ingress routing
│   ├── observability/                 # prometheus, loki, promtail, grafana
│   ├── observability-server/          # MCP server manifests
│   ├── observability-debug-agent/     # agent manifests (+ secret-example.yaml)
│   ├── namespace.yaml                 # ecommerce namespace
│   └── dockerhub-secret.yaml          # image pull secret template
├── CF/                                # AWS CloudFormation (alternative infra path)
├── scripts/                           # traffic simulation + mock data generators
│   ├── simulate_traffic_spike.py
│   ├── generate_mock_observability_data.py
│   └── TRAFFIC_SPIKE.md
├── .github/workflows/                 # CI/CD
│   ├── docker_build.yaml              # build & push all/one service (multi-arch)
│   └── ibm_cloud_build.yaml           # build → push → deploy to IBM Cloud IKS
├── start.{bat,sh} / stop.{bat,sh}     # full local build+deploy / teardown
├── restart--redeploy-service.{bat,sh} # rebuild+redeploy a single service
├── deploy-ibm-cloud.sh                # local equivalent of the IKS CI workflow
├── README.md                          # quick start
├── DEV-Readme.md                      # developer guide (URLs, queries, correlation IDs)
├── chatbot-ui-readme.md               # chat UI setup + troubleshooting
├── architecture-diagram.md            # Mermaid architecture diagram
└── demo-usecases.md                   # worked investigation examples
```

### Inside the LangGraph agent (`microservices/observability-debug-agent/app/`)
| Folder | Responsibility |
|--------|---------------|
| `api/` | FastAPI routes — `GET /health`, `POST /api/v1/investigate` |
| `graph/` | LangGraph `StateGraph` (`workflow.py`) + investigation `classification.py` |
| `mcp/` | `ObservabilityAgentClient` — HTTP client to `observability-server` |
| `correlation/` | `CorrelationEngine` — non-LLM heuristics that derive probable root cause |
| `services/` | `ReasoningService` — the OpenAI call |
| `prompts/` | LLM prompt builders (`reasoning.py`, `error_logs.py`) |
| `models/` | Pydantic schemas (request/response, findings) |
| `middleware/` | correlation-id + request-logging ASGI middleware |
| `config/` | `Settings` — env-var configuration (`get_settings()`), LRU-cached |
| `logging/` | JSON structured logger |
| `util/` | formatting (bytes/percent), Grafana deep-link builders |

### Inside the MCP server (`microservices/observability-server/.../observability/`)
| Package | Responsibility |
|---------|---------------|
| `mcp/` | `ObservabilityTools` (`@Tool` methods), `McpConfiguration` |
| `client/` | `PrometheusClient`, `LokiClient` |
| `service/` | `ObservabilityService` — orchestrates clients, shapes DTOs |
| `controller/` | REST endpoints mirroring the tools |
| `dto/` | response DTOs |
| `config/` | correlation filter, request logging, OpenAPI, properties |
| `exception/` | `GlobalExceptionHandler` |

---

## Build Commands

> Prerequisites: **Java 21, Maven, Docker Desktop (Kubernetes enabled), `kubectl`**.
> Node.js LTS is only needed for local UI development — the chat UI is baked into the agent image.

**Build one Java service (JAR):**
```bash
cd microservices/<service>          # ecommerce | product | images | observability-server
mvn clean package                   # add -DskipTests to skip tests
```

**Build a service Docker image:**
```bash
cd microservices/<service>
docker build -t <service>:local .
```

**Build the Python agent image (also builds the React UI in a Node stage):**
```bash
cd microservices/observability-debug-agent
docker build -t observability-debug-agent:local .
```

**Build the UI standalone (local dev only):**
```bash
cd microservices/observability-debug-agent/ui
npm install && npm run build          # or `npm run dev` for the Vite dev server
```

---

## Test Commands

**Java (per service):**
```bash
cd microservices/<service>
mvn test
```

**Python agent:**
```bash
cd microservices/observability-debug-agent
python -m pytest                      # tests/ — correlation engine + formatting
```

**UI type-check / build:**
```bash
cd microservices/observability-debug-agent/ui
npm run build                         # tsc + vite build; fails on type errors
```

Existing test coverage: Spring Boot context-load tests (`*ApplicationTests`), client unit
tests (`LokiClientTest`, `PrometheusClientTest`), and pytest (`test_correlation_engine`,
`test_formatting`). There is **no coverage gate configured yet** — see
[.claude/rules/rules.md](.claude/rules/rules.md) for expectations when adding code.

---

## Local Development

Full stack on Docker Desktop Kubernetes:

```powershell
# Windows
start.bat                                   # build all, deploy, wait for rollouts
stop.bat                                    # tear down workloads
restart--redeploy-service.bat <service>     # rebuild + redeploy one service
restart--redeploy-service.bat --help        # list valid service names
```
```bash
# macOS / Linux
./start.sh
./stop.sh
./restart--redeploy-service.sh <service>
```

**One-time secret** (required before the first `/api/v1/investigate` call; survives restarts):
```bash
kubectl create secret generic observability-debug-agent-secret \
  --from-literal=OPENAI_API_KEY=your-key-here -n observability
```

**Local URLs:**
| Service | URL |
|---------|-----|
| Ecommerce API | http://localhost:8090/ecommerce-service/ecommerceProducts |
| Grafana | http://localhost:3000 (`admin`/`admin` first login) |
| Prometheus | http://localhost:9090 |
| Agent chat UI | http://localhost:8092 |
| Agent Swagger | http://localhost:8092/docs |
| MCP server Swagger | http://localhost:8091/swagger-ui.html (port-forward) |

**Generate traffic to investigate:**
```bash
pip install -r scripts/requirements.txt
python scripts/simulate_traffic_spike.py     # prints correlationId per request
```

Detailed developer flows (Loki/Grafana queries, port-forwards, correlation-id tracing):
[DEV-Readme.md](DEV-Readme.md). UI setup/troubleshooting: [chatbot-ui-readme.md](chatbot-ui-readme.md).

---

## Deployment Process

### Local (Docker Desktop K8s)
`start.bat`/`start.sh` builds every image with a timestamp tag, applies the `k8s/`
manifests, `kubectl set image` to the fresh tag, and waits on `kubectl rollout status`.

### CI/CD (GitHub Actions)
- **`docker_build.yaml`** — on push/PR touching `microservices/**` (or manual dispatch):
  builds each service (Maven → JAR → Docker for Java; multi-stage Node+Python for the agent),
  multi-arch (`amd64`+`arm64`), and pushes to Docker Hub. PRs build but do **not** push.
- **`ibm_cloud_build.yaml`** — the full remote path: build + push all images, log in to
  IBM Cloud, point `kubectl` at the IKS cluster, apply namespaces + manifests, `set image`
  to the new tag, and wait on rollouts.

**Required GitHub secrets:** `DOCKER_USERNAME`, `DOCKER_PASSWORD`, `IBM_CLOUD_API_KEY`.
**Required cluster prerequisite (manual, one-time):** the `dockerhub-registry-secret`
image-pull secret in both `ecommerce` and `observability` namespaces
(see `k8s/dockerhub-secret.yaml`), and the `OPENAI_API_KEY` secret in `observability`.

`deploy-ibm-cloud.sh` is the local equivalent of the IKS workflow.

---

## Coding Standards

These principles apply to **all** languages in the repo. Match the style of the file you
are editing; the goal is code that reads as if one team wrote it.

### SOLID
- **Single Responsibility** — keep the existing layering: controllers/routes handle
  transport, services/engines hold logic, clients own external I/O, DTOs/schemas are data.
  A LangGraph node does one thing; an `@Tool` method does one thing.
- **Open/Closed** — extend the investigation by adding a graph node or an MCP tool, not by
  bolting branches onto unrelated code.
- **Liskov / Interface Segregation** — keep client interfaces narrow (metrics vs logs are
  separate concerns; keep them separate).
- **Dependency Inversion** — depend on the `ObservabilityService`/`ObservabilityAgentClient`
  abstractions, not on raw Prometheus/Loki HTTP shapes.

### Clean Code
- Names say what things are (`fetch_heap_metrics_node`, `CorrelationEngine`, `PrometheusClient`).
- Small functions; early returns; no dead code. Structured logging with a stable event name
  as the message and details in `extra`/MDC — never string-concatenate context into the message.
- Prefer **constructor injection** in Java (as `ObservabilityTools` does). The one legacy
  `@Autowired` field in `EcommerceController` is not the pattern to copy.

### DRY
- Reuse the correlation-id filter/middleware, the JSON logger, and the Grafana link builders
  — do not reimplement them. Reuse service aliases (`SERVICE_ALIASES`) and validation helpers.

### KISS
- The correlation engine is deliberately heuristic and cheap; the LLM is called once, late,
  with a compact payload. Do not add framework machinery where a function will do.

### YAGNI
- This is a focused demo/reference. Do not add caches, queues, databases, or new services
  speculatively. Add capability when a use case in `demo-usecases.md` (or a real request)
  needs it.

### Secure by Default
- **No secrets in code, config, manifests, or logs.** `OPENAI_API_KEY` and registry
  credentials come from Kubernetes secrets / GitHub secrets / environment variables only.
- Validate all inbound input (`ObservabilityTools` validates service names, time ranges,
  and step; `EcommerceController` validates coupon format). New endpoints/tools must do the same.
- Containers run as **non-root** (see the agent Dockerfile) — keep it that way.
- CORS on the agent is an explicit allow-list — extend it deliberately, never `*` with credentials.
- Never log `X-Correlation-Id` *values* as if secret, but never log request bodies, keys, or PII.

---

## Agent Guidance

How future Claude agents should work in this repository.

### How to work here
1. **Read before writing.** Start with this file, then `.claude/rules/rules.md`, then the
   specific service's source. Use the [.claude/skills](.claude/skills/) that matches your task
   (architecture-review, code-review, feature-development, troubleshooting, devops, security).
2. **Locate the seam.** Most changes fit an existing extension point:
   - New investigation data → add a graph node in `workflow.py` + a fetch in
     `ObservabilityAgentClient` + a matching MCP `@Tool` in `ObservabilityTools`.
   - New observability query → add to `ObservabilityService` + the relevant client.
   - New API behavior → the service's controller/route layer.
3. **Stay within a service.** Each of the five services builds and deploys independently.
   Cross-service changes must keep the HTTP/MCP contract and the `X-Correlation-Id` header intact.
4. **Match conventions** (see Coding Standards) rather than introducing new patterns/libraries.

### How to perform changes safely
- Make the smallest change that satisfies the request; do not refactor unrelated code.
- Preserve public contracts: REST paths, request/response schemas, MCP tool names and
  parameters, Kubernetes service names/ports, and env-var names. Changing any of these is a
  **breaking change** — follow [docs/GOVERNANCE.md](docs/GOVERNANCE.md).
- Keep correlation-id propagation and JSON logging on every new code path.
- Never commit secrets. Never weaken input validation or run containers as root.

### How to avoid regressions
- Build and test the **specific service** you touched (`mvn test` / `python -m pytest` /
  `npm run build`) before claiming done.
- For runtime-affecting changes, redeploy that one service
  (`restart--redeploy-service.* <service>`) and drive the flow end-to-end (generate traffic,
  run an `/api/v1/investigate` call, check Grafana links resolve).
- Verify the correlation-id still flows through (`{namespace="ecommerce"} |= "<id>"` in Loki).
- Do not change a shared cross-cutting concern (correlation filter, logger, link builder)
  without checking every service that uses it.

### How to generate tests
- **Java:** JUnit 5 + Spring Boot Test; mirror `LokiClientTest`/`PrometheusClientTest` for
  client/service logic and `*ApplicationTests` for context. Mock external HTTP; assert on
  parsed DTOs and on validation failures (blank service, inverted time range, non-positive step).
- **Python:** pytest; mirror `test_correlation_engine.py`/`test_formatting.py`. Unit-test the
  `CorrelationEngine` and pure `util` functions directly (no network). For graph nodes, stub
  `ObservabilityAgentClient`; for reasoning, stub the OpenAI call — **never** hit OpenAI in tests.
- Every bug fix gets a regression test that fails before the fix.

### How to review pull requests
Use the `code-review` skill. Check, in order:
1. Contracts preserved (or breaking change documented + versioned per governance).
2. Correctness + a test that proves it; correlation-id and structured logging present.
3. Security: no secrets, input validated, non-root, CORS/allow-lists tight, deps pinned.
4. Scope: no unrelated churn, no speculative abstractions (YAGNI).
5. Docs updated (this file, DEV-Readme, README, ADR if a decision was made).

---

## Forbidden Changes

Do **not** introduce any of the following without an explicit, approved ADR:

- **Secrets in the repo** — API keys, tokens, passwords, kubeconfigs in code, YAML,
  Dockerfiles, `.env` files, or logs. (`k8s/observability-debug-agent/secret-example.yaml`
  is a template only — never fill it with a real key.)
- **Silent breaking changes** to REST paths, response schemas, MCP tool names/params,
  K8s service names/ports, or env-var names.
- **Running containers as root**, adding `privileged`/`hostNetwork`/`hostPath`, or dropping
  the non-root user in a Dockerfile.
- **`allow_origins=["*"]` together with `allow_credentials=True`**, or otherwise widening CORS
  to arbitrary origins.
- **Calling OpenAI (or any paid/external LLM) from tests**, or hardcoding a model that ignores
  the `OPENAI_MODEL` setting.
- **Unpinned dependencies** — Python requirements and Maven/npm versions stay pinned; no
  floating `latest` (image `latest` tags are for local convenience only, never for reproducible deploys).
- **New databases, message brokers, service meshes, or microservices** added speculatively (YAGNI).
- **Removing or bypassing input validation** in `ObservabilityTools`, controllers, or routes.
- **Logging request bodies, secrets, tokens, or PII.**
- **Downgrading Java below 21** or changing the Spring Boot / Spring AI major line without an ADR.
- **`--force` pushes to `master`/`main`**, skipping CI, or merging without the mandatory checks
  in [.claude/rules/rules.md](.claude/rules/rules.md).
```