# microservices-ecommerce-2 — Architecture

Kubernetes-native ecommerce demo with full observability stack and AI-assisted investigation. Deployed locally via `start.bat` on Docker Desktop Kubernetes.

## System context

```mermaid
flowchart TB
    subgraph External["External / host machine"]
        USER["User / browser"]
        DEV["Developer"]
        SCRIPT["simulate_traffic_spike.py"]
        OPENAI["OpenAI API"]
    end

    subgraph K8s["Kubernetes cluster (Docker Desktop)"]
        subgraph EcomNS["namespace: ecommerce"]
            EC["ecommerce<br/>Spring Boot :8090"]
            PR["product<br/>Spring Boot :8090"]
            IM["images<br/>Spring Boot :8090"]
            H2P[("H2 in-memory<br/>productsdb")]
            H2I[("H2 in-memory<br/>imagesdb")]
            ING["Ingress nginx<br/>optional paths"]
        end

        subgraph ObsNS["namespace: observability"]
            PROM["Prometheus :9090"]
            LOKI["Loki :3100"]
            GRAF["Grafana :3000"]
            PT["Promtail DaemonSet"]
            OAG["observability-server<br/>Spring Boot :8091"]
            TALK["talk-to-observability-agent<br/>FastAPI :8092"]
            UI["Chat UI React<br/>bundled in TALK static/"]
        end
    end

    USER -->|"LoadBalancer :8090"| EC
    USER -->|"LoadBalancer :8092"| UI
    USER -->|"LoadBalancer :3000"| GRAF
    USER -->|"LoadBalancer :9090"| PROM
    DEV -->|"kubectl port-forward :8091"| OAG

    SCRIPT -->|"HTTP + X-Correlation-Id"| EC

    EC -->|"GET /product-service"| PR
    EC -->|"GET /image-service"| IM
    PR --> H2P
    IM --> H2I

    ING -.-> EC
    ING -.-> PR
    ING -.-> IM

    PROM -->|"scrape /actuator/prometheus"| EC
    PROM --> PR
    PROM --> IM

    EC & PR & IM & OAG & TALK -->|"stdout JSON logs"| PT
    PT -->|"push LogQL labels"| LOKI

    GRAF --> PROM
    GRAF --> LOKI

    UI --> TALK
    TALK -->|"LangGraph POST /api/v1/investigate"| TALK
    TALK -->|"REST /api/observability/*"| OAG
    TALK -->|"Grafana API UID lookup"| GRAF
    TALK --> OPENAI
    OAG --> LOKI
    OAG --> PROM

    classDef spring fill:#e8f5e9,stroke:#2e7d32
    classDef python fill:#fff3e0,stroke:#ef6c00
    classDef obs fill:#e3f2fd,stroke:#1565c0
    classDef store fill:#fce4ec,stroke:#c2185b
    classDef ext fill:#f5f5f5,stroke:#616161

    class EC,PR,IM,OAG spring
    class TALK,UI,SCRIPT python
    class PROM,LOKI,GRAF,PT obs
    class H2P,H2I store
    class USER,DEV,OPENAI ext
```

## Kubernetes layout

```mermaid
flowchart LR
    subgraph Deploy["start.bat / stop.bat"]
        BAT["Build Maven + Docker images<br/>timestamp tag → kubectl apply"]
    end

    subgraph NS1["ecommerce"]
        direction TB
        D1["Deployments:<br/>ecommerce, product, images"]
        S1["Services:<br/>ecommerce-service LoadBalancer :8090<br/>product-service ClusterIP :8090<br/>images-service ClusterIP :8090"]
        I1["Ingress: ecommerce-ingress"]
        D1 --> S1
        I1 --> S1
    end

    subgraph NS2["observability"]
        direction TB
        D2["Deployments:<br/>prometheus, loki, grafana<br/>observability-server, talk-to-observability-agent"]
        DS2["DaemonSet: promtail"]
        S2["Services:<br/>prometheus, grafana LoadBalancer<br/>loki, observability-server ClusterIP<br/>talk-to-observability-agent LoadBalancer :8092"]
        CM2["ConfigMaps + Secret<br/>OPENAI_API_KEY"]
        D2 --> S2
        DS2 --> LOKI2["loki :3100"]
        CM2 --> D2
    end

    BAT --> NS1
    BAT --> NS2

    LOKI2
```

## Ecommerce request flow

```mermaid
sequenceDiagram
    participant C as Client / traffic script
    participant E as ecommerce-service
    participant P as product-service
    participant I as images-service

    C->>E: GET /ecommerce-service/ecommerceProducts<br/>X-Correlation-Id (optional)
    Note over E: CorrelationIdFilter → MDC correlationId<br/>JSON log to stdout
    E->>P: GET /product-service/...<br/>propagate X-Correlation-Id
    E->>I: GET /image-service/...<br/>propagate X-Correlation-Id
    P-->>E: products
    I-->>E: images
    E-->>C: aggregated response + X-Correlation-Id header
```

## Observability data plane

```mermaid
flowchart LR
    subgraph Apps["Spring Boot apps (ecommerce namespace)"]
        A1["ecommerce"]
        A2["product"]
        A3["images"]
    end

    subgraph Agents["observability namespace"]
        A4["observability-server"]
        A5["talk-to-observability-agent"]
    end

    subgraph Telemetry["Telemetry stack"]
        PRM["Prometheus"]
        LOK["Loki"]
        GRF["Grafana"]
        PRT["Promtail"]
    end

    A1 & A2 & A3 -->|"Micrometer metrics<br/>/actuator/prometheus"| PRM
    A1 & A2 & A3 & A4 & A5 -->|"JSON logs correlationId"| PRT
    PRT --> LOK
    GRF --> PRM
    GRF --> LOK

    A5 -->|"LogQL / PromQL via REST"| A4
    A4 --> LOK
    A4 --> PRM
```

## Investigation / chatbot flow

```mermaid
sequenceDiagram
    participant U as Browser :8092
    participant T as talk-to-observability-agent
    participant LG as LangGraph workflow
    participant O as observability-server
    participant L as Loki
    participant P as Prometheus
    participant AI as OpenAI
    participant G as Grafana

    U->>T: Chat message → POST /api/v1/investigate
    T->>LG: ainvoke(state)
    LG->>O: list services, logs, metrics
    O->>L: LogQL query_range
    O->>P: PromQL query
    LG->>LG: correlation_node (deterministic)
    LG->>AI: reasoning_node summary
    LG->>G: resolve datasource/dashboard UIDs
    LG-->>T: summary, evidence, grafana URLs
    T-->>U: InvestigationResponse + UI render
```

LangGraph node detail: [`workflow-diagram.md`](microservices/talk-to-observability-agent/app/graph/workflow-diagram.md)

## Service catalog

| Component | Tech | Namespace | K8s Service | Exposure | Role |
|-----------|------|-----------|-------------|----------|------|
| ecommerce | Java 21, Spring Boot 3.3 | ecommerce | `ecommerce-service` | LoadBalancer `:8090` | BFF; aggregates product + images |
| product | Java 21, Spring Boot 3.3 | ecommerce | `product-service` | ClusterIP `:8090` | Product catalog (H2) |
| images | Java 21, Spring Boot 3.3 | ecommerce | `images-service` | ClusterIP `:8090` | Image metadata (H2) |
| Ingress | nginx | ecommerce | `ecommerce-ingress` | Ingress rules | Optional path routing |
| Prometheus | Prometheus | observability | `prometheus` | LoadBalancer `:9090` | Scrapes JVM/HTTP metrics |
| Loki | Grafana Loki | observability | `loki` | ClusterIP `:3100` | Log aggregation |
| Promtail | Promtail | observability | DaemonSet | — | Ships pod logs → Loki |
| Grafana | Grafana | observability | `grafana` | LoadBalancer `:3000` | Dashboards + Explore |
| observability-server | Java, Spring Boot, MCP | observability | `observability-server` | ClusterIP `:8091` | REST + MCP → Loki/Prometheus |
| talk-to-observability-agent | Python, FastAPI, LangGraph | observability | `talk-to-observability-agent` | LoadBalancer `:8092` | NL investigation + chat UI |
| traffic script | Python aiohttp | host | — | `localhost:8090` | Demo load + correlation IDs |

## Correlation ID

- Header: `X-Correlation-Id` (generated or forwarded)
- Logged as `correlationId` in JSON stdout → Promtail → Loki
- Used by observability-server LogQL and chat investigations

## Local URLs (after `start.bat`)

| URL | Target |
|-----|--------|
| http://localhost:8090/ecommerce-service/ecommerceProducts | ecommerce API |
| http://localhost:3000 | Grafana |
| http://localhost:9090 | Prometheus |
| http://localhost:8092 | Observability chat UI |
| http://localhost:8092/docs | FastAPI Swagger |
| http://localhost:8091/swagger-ui.html | observability-server *(port-forward)* |

## Repository map (runtime)

```
microservices/
  ecommerce/          → Deployment in ecommerce
  product/            → Deployment in ecommerce
  images/             → Deployment in ecommerce
  observability-server/→ Deployment in observability
  talk-to-observability-agent/
    app/graph/        → LangGraph workflow
    ui/               → React chat (built into image)
k8s/
  ecommerce/          → app manifests
  ingress/
  observability/      → prometheus, loki, promtail, grafana
  observability-server/
  talk-to-observability-agent/
start.bat / stop.bat  → local orchestration
```
