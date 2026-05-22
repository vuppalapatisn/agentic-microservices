# microservices-ecommerce-2

- Why did the request respond so slowly in production ?
- What is the heap usage right now ?
- What are the details of this error when calling an API ?

These are some very common questions that a software engineer faces for their production applications. They need to have strong basics on memory management and log analysis. So, in case of Java applications, they would need to check monitoring in application like graphana. See the trends for heap size, request rate, GC etc. They would also need to correlate the logs in log aggregators like splunk or graylog for microservices.

In case of production issues, time is especially critical and if we can automate that analysis, we are able to solve the problems faster.

With this problem statement in mind, I built an observability MCP server and an observability agent to help users in faster analysis of such issues. The MCP server reads aggregated logs and monitoring data (like JVM heap size, request rate) of microservices. Agent gathers this data as per the request and sends to LLM. LLM makes sense of the data and provides needed information of the investigation.
I have used Spring AI for MCP server and langgraph for the agent. I use OpenAI API for LLM.
![Architecture Diagram](Architecture%20diagram.png)

## Demo use cases

Slow requests, stack trace details from logs, and heap usage %: **[demo-usecases.md](demo-usecases.md)**


## Prerequisites

Java 21, Maven, Docker Desktop (Kubernetes enabled), `kubectl`

Optional for UI development only: Node.js LTS (`npm` on PATH). Not required when using `start.bat` — the chat UI is built into the Docker image.

## Start / Stop

```powershell
cd C:\git\microservices-ecommerce-2
start.bat    # build, deploy, wait for rollouts
stop.bat     # tear down workloads
restart--redeploy-service.bat observability-debug-agent   # rebuild one service
restart--redeploy-service.bat --help                        # list all service names
```

## URLs (localhost)

| Service | URL |
|---------|-----|
| Ecommerce API | http://localhost:8090/ecommerce-service/ecommerceProducts |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| observability-debug-agent (chat UI) | http://localhost:8092 |
| observability-debug-agent (Swagger) | http://localhost:8092/docs |
| observability-server (Swagger) | http://localhost:8091/swagger-ui.html *(port-forward)* |

Chat UI setup and troubleshooting: **[chatbot-ui-readme.md](chatbot-ui-readme.md)**

Architecture (Mermaid): **[architecture-diagram.md](architecture-diagram.md)**

## Before first investigate call

Create OpenAI secret once in namespace `observability` (survives `start.bat`). **cmd.exe** — single line:

```bat
kubectl create secret generic observability-debug-agent-secret --from-literal=OPENAI_API_KEY=your-key-here -n observability
```

## Developer guide

APIs, Swagger, Loki/Grafana queries, correlation IDs, port-forwards: **[DEV-Readme.md](DEV-Readme.md)**
