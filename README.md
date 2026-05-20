# microservices-ecommerce-2

Kubernetes-native ecommerce demo with Prometheus, Loki, Grafana, observability-agent, and talk-to-observability-agent.

## Prerequisites

Java 21, Maven, Docker Desktop (Kubernetes enabled), `kubectl`

## Start / Stop

```powershell
cd C:\git\microservices-ecommerce-2
start.bat    # build, deploy, wait for rollouts
stop.bat     # tear down workloads
```

## URLs (localhost)

| Service | URL |
|---------|-----|
| Ecommerce API | http://localhost:8090/ecommerce-service/ecommerceProducts |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| Talk-to-observability (Swagger) | http://localhost:8092/docs |
| Observability-agent (Swagger) | http://localhost:8091/swagger-ui.html *(port-forward)* |

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

APIs, Swagger, Loki/Grafana queries, correlation IDs, port-forwards: **[dev-readme.md](dev-readme.md)**
