# microservices-ecommerce-2

Kubernetes-native ecommerce demo with Prometheus, Loki, Grafana, observability-agent, talk-to-observability-agent, and a browser chat UI for investigations.

## Prerequisites

Java 21, Maven, Docker Desktop (Kubernetes enabled), `kubectl`

Optional for UI development only: Node.js LTS (`npm` on PATH). Not required when using `start.bat` — the chat UI is built into the Docker image.

## Start / Stop

```powershell
cd C:\git\microservices-ecommerce-2
start.bat    # build, deploy, wait for rollouts
stop.bat     # tear down workloads
restart--redeploy-service.bat talk-to-observability-agent   # rebuild one service
restart--redeploy-service.bat --help                        # list all service names
```

## URLs (localhost)

| Service | URL |
|---------|-----|
| Ecommerce API | http://localhost:8090/ecommerce-service/ecommerceProducts |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| Observability chatbot UI | http://localhost:8092 |
| Talk-to-observability (Swagger) | http://localhost:8092/docs |
| Observability-agent (Swagger) | http://localhost:8091/swagger-ui.html *(port-forward)* |

Chat UI setup and troubleshooting: **[chatbot-ui-readme.md](chatbot-ui-readme.md)**

Architecture (Mermaid): **[architecture-diagram.md](architecture-diagram.md)**

## Before first investigate call

Create OpenAI secret once in namespace `observability` (survives `start.bat`):

```powershell
kubectl create secret generic talk-to-observability-agent-secret `
  --from-literal=OPENAI_API_KEY=your-key-here `
  -n observability
```

## Traffic spike demo

```powershell
pip install -r scripts/requirements.txt
python scripts/simulate_traffic_spike.py
```

## Developer guide

APIs, Swagger, Loki/Grafana queries, correlation IDs, port-forwards: **[DEV-Readme.md](DEV-Readme.md)**
