# Agentic Microservices — Architecture, Data Flow, Component & Deployment Diagrams

Kubernetes-native ecommerce demo with a full observability stack and an **agentic AI debug assistant**. An
`observability-server` (Spring Boot + MCP) exposes Loki/Prometheus data over REST, and an
`observability-debug-agent` (FastAPI + LangGraph) turns natural-language questions into automated investigations.

> Primary deployment target is **IBM Cloud Kubernetes Service (IKS)** using **IBM Container Registry (`in.icr.io`)**.
> The repo also ships local Docker Desktop K8s manifests (`k8s/`) and an AWS ECS Fargate alternative (`CF/`).

---

## 1. Project Architecture (High-Level)

```mermaid
flowchart TB
    subgraph External["External actors"]
        USER["User / Browser"]
        DEV["Developer"]
        LLM["OpenAI-compatible LLM API<br/>(gpt-4.1-mini)"]
    end

    subgraph App["Application plane — namespace: ecommerce"]
        EC["ecommerce (BFF)<br/>Spring Boot :8090"]
        PR["product<br/>Spring Boot :8090"]
        IM["images<br/>Spring Boot :8090"]
        H2P[("H2 in-memory<br/>productsdb")]
        H2I[("H2 in-memory<br/>imagesdb")]
    end

    subgraph Obs["Observability + AI plane — namespace: observability"]
        UI["Chat UI (React/Vite)<br/>bundled in agent image"]
        ODA["observability-debug-agent<br/>FastAPI + LangGraph :8092"]
        OSV["observability-server<br/>Spring Boot + MCP :8091"]
        PROM["Prometheus :9090"]
        LOKI["Loki :3100"]
        PT["Promtail (DaemonSet)"]
        GRAF["Grafana :3000"]
    end

    USER -->|browse products| EC
    USER -->|ask questions| UI
    USER -->|dashboards| GRAF
    DEV  -->|kubectl port-forward| OSV

    EC --> PR & IM
    PR --> H2P
    IM --> H2I

    UI --> ODA
    ODA -->|REST /api/observability/*| OSV
    ODA -->|reasoning| LLM
    ODA -->|resolve dashboard/datasource UIDs| GRAF

    OSV -->|LogQL| LOKI
    OSV -->|PromQL| PROM

    EC & PR & IM -->|Micrometer /actuator/prometheus| PROM
    EC & PR & IM & OSV & ODA -->|JSON stdout logs| PT
    PT --> LOKI
    GRAF --> PROM & LOKI

    classDef spring fill:#e8f5e9,stroke:#2e7d32,color:#000
    classDef python fill:#fff3e0,stroke:#ef6c00,color:#000
    classDef obs fill:#e3f2fd,stroke:#1565c0,color:#000
    classDef store fill:#fce4ec,stroke:#c2185b,color:#000
    classDef ext fill:#f5f5f5,stroke:#616161,color:#000

    class EC,PR,IM,OSV spring
    class ODA,UI python
    class PROM,LOKI,GRAF,PT obs
    class H2P,H2I store
    class USER,DEV,LLM ext
```

**Two planes, one cluster:**
- **Application plane** (`ecommerce`): the demo workload that generates telemetry — a BFF (`ecommerce`) fanning out to `product` and `images`.
- **Observability + AI plane** (`observability`): collects telemetry (Promtail → Loki, scrape → Prometheus), visualizes it (Grafana), and reasons over it (`observability-server` → `observability-debug-agent` → LLM).

---

## 2. Data Flow Diagrams

### 2a. Telemetry ingestion (how data gets collected)

```mermaid
flowchart LR
    subgraph Apps["Instrumented apps"]
        A1["ecommerce"]
        A2["product"]
        A3["images"]
        A4["observability-server"]
        A5["observability-debug-agent"]
    end

    A1 & A2 & A3 -->|"HTTP + JVM metrics<br/>GET /actuator/prometheus (pull)"| PRM["Prometheus<br/>TSDB"]
    A1 & A2 & A3 & A4 & A5 -->|"structured JSON logs → stdout"| STDOUT["Pod stdout"]
    STDOUT -->|"tail container logs"| PT["Promtail<br/>DaemonSet"]
    PT -->|"push w/ labels: app, level, correlationId"| LOK["Loki<br/>log store"]

    PRM --> GRF["Grafana"]
    LOK --> GRF

    classDef obs fill:#e3f2fd,stroke:#1565c0,color:#000
    class PRM,LOK,GRF,PT obs
```

Every log line carries a `correlationId` (from the `X-Correlation-Id` header via `CorrelationIdFilter`/MDC), which is the join key for cross-service investigations.

### 2b. AI investigation data flow (request → answer)

```mermaid
sequenceDiagram
    autonumber
    participant U as Browser (Chat UI :8092)
    participant API as FastAPI routes<br/>/api/v1/investigate
    participant WF as LangGraph workflow
    participant CL as ObservabilityAgentClient (httpx)
    participant OSV as observability-server :8091
    participant L as Loki
    participant P as Prometheus
    participant AI as LLM (OpenAI-compatible)
    participant G as Grafana API

    U->>API: POST {query, correlationId?}
    API->>WF: run(request, correlationId)

    Note over WF: parse_query → identify_service → identify_time_range → build_plan
    WF->>WF: classify_investigation() sets fetch_* flags

    alt needs logs
        WF->>CL: get_logs / get_error_logs
        CL->>OSV: GET /api/observability/logs/*
        OSV->>L: LogQL query_range
        L-->>OSV: log lines
        OSV-->>CL: LogsResponseDto
    end
    alt needs monitoring
        WF->>CL: get_heap / thread / request-rate metrics
        CL->>OSV: GET /api/observability/metrics/*
        OSV->>P: PromQL query_range
        P-->>OSV: series points
        OSV-->>CL: MetricsResponseDto
    end

    WF->>WF: correlation_node (deterministic root-cause)
    WF->>AI: reasoning_node — summarize evidence
    AI-->>WF: natural-language summary
    WF->>G: resolve Loki/dashboard UIDs → build deep links
    WF-->>API: InvestigationResponse{summary, rootCause, evidence, grafanaUrls}
    API-->>U: JSON → rendered in chat
```

The `correlationId` propagates on every hop (`X-Correlation-Id` header), so the agent's own activity is traceable in the same Loki stream it queries.

---

## 3. Component Architecture

### 3a. observability-debug-agent (FastAPI + LangGraph — Python)

```mermaid
flowchart TB
    subgraph ODA["observability-debug-agent (app/)"]
        direction TB

        subgraph Edge["Entry / Edge"]
            MAIN["main.py<br/>FastAPI app + lifespan"]
            MW1["CorrelationIdMiddleware"]
            MW2["RequestLoggingMiddleware"]
            STATIC["StaticFiles → React chat UI<br/>(/, /assets)"]
            ROUTES["api/routes.py<br/>POST /api/v1/investigate · GET /health"]
        end

        subgraph Graph["LangGraph workflow (graph/)"]
            WF["workflow.py<br/>InvestigationWorkflow / StateGraph"]
            CLS["classification.py<br/>classify_investigation()"]
            NODES["nodes: parse_query · identify_service ·<br/>identify_time_range · build_plan ·<br/>fetch_logs · fetch_error_logs ·<br/>fetch_heap · fetch_thread · fetch_request_rate ·<br/>correlation · reasoning · response"]
        end

        subgraph Domain["Domain logic"]
            CORR["correlation/engine.py<br/>CorrelationEngine (deterministic root cause)"]
            REASON["services/reasoning_service.py<br/>LLM summarization"]
            PROMPTS["prompts/*<br/>reasoning · error_logs templates"]
            SCHEMAS["models/schemas.py<br/>Pydantic DTOs"]
        end

        subgraph Integrations["Outbound"]
            MCP["mcp/observability_client.py<br/>httpx AsyncClient"]
            GLINKS["util/grafana_links.py<br/>Explore/dashboard deep links"]
            FMT["util/formatting.py"]
            CFG["config/settings.py (env)"]
            LOG["logging/json_logger.py"]
        end
    end

    OSV["observability-server :8091"]
    LLM["LLM API"]
    GRAF["Grafana API"]

    MAIN --> MW1 --> MW2 --> ROUTES
    MAIN --> STATIC
    ROUTES --> WF
    WF --> CLS & NODES
    NODES --> CORR
    NODES --> REASON --> PROMPTS
    NODES --> MCP --> OSV
    NODES --> GLINKS --> GRAF
    REASON --> LLM
    WF -.reads.-> CFG
    WF -.-> SCHEMAS
    CORR -.-> SCHEMAS

    classDef py fill:#fff3e0,stroke:#ef6c00,color:#000
    classDef ext fill:#f5f5f5,stroke:#616161,color:#000
    class MAIN,MW1,MW2,STATIC,ROUTES,WF,CLS,NODES,CORR,REASON,PROMPTS,SCHEMAS,MCP,GLINKS,FMT,CFG,LOG py
    class OSV,LLM,GRAF ext
```

### 3b. LangGraph investigation state machine (conditional routing)

```mermaid
flowchart TD
    START([entry]) --> PQ[parse_query_node]
    PQ --> IS[identify_service_node]
    IS --> ITR[identify_time_range_node]
    ITR --> BP[build_investigation_plan_node]

    BP -->|needs_logs| FL[fetch_logs_node]
    BP -->|needs_monitoring only| FH[fetch_heap_metrics_node]
    BP -->|neither| CR[correlation_node]

    FL -->|fetch_error_logs| FE[fetch_error_logs_node]
    FL -->|else| CR
    FE -->|fetch_heap_metrics| FH
    FE -->|else| CR

    FH -->|fetch_thread_metrics| FT[fetch_thread_metrics_node]
    FH -->|else| CR
    FT --> FR[fetch_request_rate_node]
    FR --> CR

    CR --> RE[reasoning_node]
    RE --> RS[response_node]
    RS --> END([END])

    classDef node fill:#fff3e0,stroke:#ef6c00,color:#000
    class PQ,IS,ITR,BP,FL,FE,FH,FT,FR,CR,RE,RS node
```

### 3c. observability-server (Spring Boot + MCP — Java)

```mermaid
flowchart TB
    subgraph OSV["observability-server (com.amol...observability)"]
        direction TB
        APP["ObservabilityServerApplication"]

        subgraph Web["Web layer"]
            CTRL["ObservabilityController<br/>REST /api/observability/*"]
            SWAG["OpenApiConfig (Swagger)"]
            F1["CorrelationIdFilter"]
            F2["RequestLoggingFilter"]
            GEH["GlobalExceptionHandler"]
        end

        subgraph MCPL["MCP layer"]
            MCFG["McpConfiguration"]
            TOOLS["ObservabilityTools<br/>(MCP tool functions)"]
        end

        subgraph Svc["Service + clients"]
            SVC["ObservabilityService"]
            LC["LokiClient"]
            PC["PrometheusClient"]
            PROPS["ObservabilityProperties"]
        end

        DTO["dto/*: Logs, Metrics, Services,<br/>ErrorResponse DTOs"]
    end

    LOKI["Loki :3100"]
    PROM["Prometheus :9090"]

    CTRL --> SVC
    TOOLS --> SVC
    MCFG --> TOOLS
    SVC --> LC --> LOKI
    SVC --> PC --> PROM
    SVC --> DTO
    SVC -.reads.-> PROPS

    classDef spring fill:#e8f5e9,stroke:#2e7d32,color:#000
    classDef obs fill:#e3f2fd,stroke:#1565c0,color:#000
    class APP,CTRL,SWAG,F1,F2,GEH,MCFG,TOOLS,SVC,LC,PC,PROPS,DTO spring
    class LOKI,PROM obs
```

The server exposes the **same capabilities two ways**: plain REST (used by the debug agent) and **MCP tools** (usable by any MCP-compatible client).

---

## 4. Deployment Diagrams

### 4a. IBM Cloud Kubernetes Service (primary target)

```mermaid
flowchart TB
    subgraph CICD["CI/CD — GitHub Actions"]
        GH[".github/workflows<br/>ibm_cloud_icr_build.yaml"]
        ICR[["IBM Container Registry<br/>in.icr.io/agentic/*"]]
        GH -->|docker build + push| ICR
    end

    subgraph IKS["IBM Cloud Kubernetes Service (IKS) cluster"]
        subgraph NSE["namespace: ecommerce"]
            DEC["Deployment ecommerce → Svc LoadBalancer :8090"]
            DPR["Deployment product → Svc ClusterIP :8090"]
            DIM["Deployment images → Svc ClusterIP :8090"]
            INGE["Ingress: ecommerce-ingress"]
        end
        subgraph NSO["namespace: observability"]
            DODA["Deployment observability-debug-agent<br/>Svc LoadBalancer :8092<br/>req 100m/256Mi · lim 300m/512Mi"]
            DOSV["Deployment observability-server → Svc ClusterIP :8091"]
            DPROM["Deployment prometheus → Svc LB :9090 (+RBAC)"]
            DLOKI["Deployment loki → Svc ClusterIP :3100"]
            DGRAF["Deployment grafana → Svc LB :3000"]
            DSPT["DaemonSet promtail (+RBAC)"]
            CMS["ConfigMaps (per service)"]
            SEC["Secret: observability-debug-agent-secret<br/>(OPENAI_API_KEY)"]
            IPS["imagePullSecret: in-icr-io-secret"]
        end
    end

    LLM["OpenAI-compatible LLM API"]
    USER["Users / Browser"]

    ICR -.image pull via in-icr-io-secret.-> IKS
    USER --> INGE
    USER --> DODA & DGRAF & DPROM & DEC
    DODA --> DOSV --> DLOKI & DPROM
    DODA --> LLM
    CMS --> DODA & DOSV
    SEC --> DODA
    IPS --> DODA

    classDef k8s fill:#e8eaf6,stroke:#3949ab,color:#000
    classDef ext fill:#f5f5f5,stroke:#616161,color:#000
    class DEC,DPR,DIM,INGE,DODA,DOSV,DPROM,DLOKI,DGRAF,DSPT,CMS,SEC,IPS k8s
    class LLM,USER,ICR,GH ext
```

Health gating: the debug agent has `startup`, `readiness`, and `liveness` probes on `GET /health :8092`; at startup it retries `observability-server` (up to 30×) and requires `OPENAI_API_KEY` before serving.

### 4b. Local — Docker Desktop Kubernetes (`start.bat`)

```mermaid
flowchart LR
    subgraph Host["Developer host machine"]
        BAT["start.bat / stop.bat<br/>Maven + docker build<br/>timestamp tag → kubectl apply"]
        BROWSER["Browser :8090 :8092 :3000 :9090"]
        PF["kubectl port-forward :8091"]
    end

    subgraph DD["Docker Desktop Kubernetes"]
        NS1["namespace ecommerce<br/>ecommerce · product · images"]
        NS2["namespace observability<br/>prometheus · loki · promtail ·<br/>grafana · observability-server ·<br/>observability-debug-agent"]
    end

    BAT --> NS1 & NS2
    BROWSER --> NS1 & NS2
    PF --> NS2

    classDef k8s fill:#e8eaf6,stroke:#3949ab,color:#000
    class NS1,NS2 k8s
```

Before the first investigation, create the secret once:
`kubectl create secret generic observability-debug-agent-secret --from-literal=OPENAI_API_KEY=... -n observability`

### 4c. AWS ECS Fargate alternative (`CF/` CloudFormation)

```mermaid
flowchart TB
    INET["Internet"] --> ALB["Application Load Balancer :80<br/>path-based listener rules"]

    subgraph VPC["VPC (public + private subnets, NAT)"]
        subgraph ECS["ECS Fargate cluster"]
            T1["ecommerce :8090 — /ecommerceApp*"]
            T2["product :8090 — /product-service*"]
            T3["images :8090 — /image-service*"]
            T4["observability-server :8091 — /actuator*"]
            T5["observability-debug-agent :8092 — /observability*, /health"]
            T6["prometheus :9090 — /prometheus*"]
            T7["grafana :3000 — /grafana*"]
            T8["loki :3100 — /loki*"]
        end
        CMAP["Cloud Map private DNS<br/>*.agentic-microservices.local"]
        CW["CloudWatch Logs<br/>/ecs/agentic-microservices/*"]
        SM["Secrets Manager<br/>(OPENAI_API_KEY, Grafana pwd)"]
    end

    DH[["Docker Hub<br/>sudhavuppalapati/*"]]

    ALB --> T1 & T2 & T3 & T4 & T5 & T6 & T7 & T8
    T1 --> T2 & T3
    T5 --> T4 --> T8 & T6
    ECS -.resolve internal.-> CMAP
    ECS -.logs.-> CW
    ECS -.secrets.-> SM
    DH -.image pull.-> ECS

    classDef aws fill:#fff8e1,stroke:#ff8f00,color:#000
    class ALB,T1,T2,T3,T4,T5,T6,T7,T8,CMAP,CW,SM aws
```

Three stacks deploy in order: `01-infrastructure` (VPC/ALB/ECS/IAM/Secrets/CloudWatch) → `02-microservices` → `03-observability`.

---

## 5. Deployment target comparison

| Aspect | IBM Cloud (IKS) | Local (Docker Desktop) | AWS (ECS Fargate) |
|---|---|---|---|
| Orchestrator | Kubernetes | Kubernetes | ECS Fargate |
| Manifests | `k8s/` | `k8s/` + `start.bat` | `CF/*.yaml` (CloudFormation) |
| Image registry | `in.icr.io/agentic/*` | local build | Docker Hub `sudhavuppalapati/*` |
| Ingress / edge | Ingress + LoadBalancer Svc | LoadBalancer Svc | ALB path rules |
| Service discovery | K8s DNS (`*.svc.cluster.local`) | K8s DNS | Cloud Map (`*.local`) |
| Log store | Loki (in-cluster) | Loki | Loki + CloudWatch |
| Secrets | K8s Secret | K8s Secret | Secrets Manager |
| Pull secret | `in-icr-io-secret` | none | task execution role |

---

## Service catalog

| Component | Tech | Namespace | Exposure | Role |
|---|---|---|---|---|
| ecommerce | Java 21, Spring Boot 3.3 | ecommerce | LoadBalancer :8090 | BFF; aggregates product + images |
| product | Java 21, Spring Boot 3.3 | ecommerce | ClusterIP :8090 | Product catalog (H2) |
| images | Java 21, Spring Boot 3.3 | ecommerce | ClusterIP :8090 | Image metadata (H2) |
| observability-server | Java, Spring Boot, MCP | observability | ClusterIP :8091 | REST + MCP → Loki/Prometheus |
| observability-debug-agent | Python, FastAPI, LangGraph | observability | LoadBalancer :8092 | NL investigation + chat UI |
| Prometheus | Prometheus | observability | LoadBalancer :9090 | Scrapes JVM/HTTP metrics |
| Loki | Grafana Loki | observability | ClusterIP :3100 | Log aggregation |
| Promtail | Promtail | observability | DaemonSet | Ships pod logs → Loki |
| Grafana | Grafana | observability | LoadBalancer :3000 | Dashboards + Explore |

> Related: high-level views in [architecture-diagram.md](architecture-diagram.md); LangGraph node detail in
> [microservices/observability-debug-agent/app/graph/workflow-diagram.md](microservices/observability-debug-agent/app/graph/workflow-diagram.md).
