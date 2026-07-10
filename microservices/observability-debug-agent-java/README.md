# observability-debug-agent-java

Spring Boot + Spring AI port of the Python `observability-debug-agent` microservice.

## Tech Stack

| Concern | Python original | Java equivalent |
|---|---|---|
| HTTP framework | FastAPI | Spring Boot 3.3 (Spring MVC) |
| AI orchestration | LangGraph StateGraph | Sequential workflow with virtual threads |
| AI model | OpenAI gpt-4.1-mini via `openai` SDK | Spring AI `ChatClient` (OpenAI starter) |
| HTTP client | `httpx` async | Spring WebFlux `WebClient` |
| Data validation | Pydantic v2 | Bean Validation + Jackson |
| Parallel fetch | `asyncio.gather` | `CompletableFuture` + virtual threads (Java 21) |
| Middleware | Starlette BaseHTTPMiddleware | `OncePerRequestFilter` |
| Config | `os.getenv` + `@lru_cache` | `@ConfigurationProperties` + Spring beans |

## Running Locally

```bash
export OPENAI_API_KEY=sk-...
export OBSERVABILITY_AGENT_BASE_URL=http://localhost:8091

mvn spring-boot:run
```

The API starts on port **8092** — the same port as the Python version.

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Kubernetes readiness/liveness probe |
| POST | `/api/v1/investigate` | Main investigation endpoint |

### Investigate Request

```json
{
  "query": "Why is ecommerce-service slow?",
  "correlationId": "optional-uuid"
}
```

### Investigate Response

```json
{
  "investigationId": "uuid",
  "correlationId": "uuid",
  "summary": "AI-generated root cause summary...",
  "probableRootCause": "resource saturation",
  "evidence": ["Heap averaged 512.00 MB, peaked at 820.00 MB", "..."],
  "grafanaExploreUrl": "http://localhost:3000/explore?...",
  "grafanaDashboardUrl": "http://localhost:3000/d/ecommerce-observability?..."
}
```

## Architecture

```
HTTP Request
    │
    ├─ CorrelationIdFilter      (reads/generates X-Correlation-Id, sets MDC)
    ├─ RequestLoggingFilter     (logs method, path, status, durationMs)
    │
    └─ InvestigationController  POST /api/v1/investigate
           │
           └─ InvestigationWorkflow
                  │
                  ├─ 1. identifyService()          regex/alias matching
                  ├─ 2. setTimeRange()             last 30 minutes
                  ├─ 3. InvestigationClassifier    keyword → fetch flags
                  ├─ 4. fetchData()                parallel CompletableFuture
                  │       ├─ ObservabilityClient.getLogsByService()
                  │       ├─ ObservabilityClient.getErrorLogsByService()
                  │       ├─ ObservabilityClient.getHeapMetrics()
                  │       ├─ ObservabilityClient.getHeapMaxMetrics()
                  │       ├─ ObservabilityClient.getThreadMetrics()
                  │       └─ ObservabilityClient.getRequestRate()
                  ├─ 5. CorrelationEngine          scoring heuristics
                  ├─ 6. ReasoningService           Spring AI ChatClient
                  └─ 7. GrafanaLinks               Explore + Dashboard URLs
```

## Building Docker Image

```bash
docker build -t observability-debug-agent-java:latest .
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `OPENAI_API_KEY` | (required) | OpenAI API key |
| `OPENAI_MODEL` | `gpt-4.1-mini` | OpenAI model name |
| `OBSERVABILITY_AGENT_BASE_URL` | Kubernetes DNS | observability-server base URL |
| `REQUEST_TIMEOUT_SECONDS` | `10` | HTTP timeout for MCP calls |
| `STARTUP_VALIDATION_RETRIES` | `30` | Startup retry attempts |
| `STARTUP_VALIDATION_RETRY_SECONDS` | `2` | Seconds between retries |
| `GRAFANA_BASE_URL` | `http://localhost:3000` | Public Grafana URL (for links) |
| `GRAFANA_API_BASE_URL` | Kubernetes DNS | Internal Grafana API URL |
| `GRAFANA_LOKI_DATASOURCE_UID` | `loki` | Loki datasource UID |
| `GRAFANA_DASHBOARD_UID` | `ecommerce-observability` | Dashboard UID |
