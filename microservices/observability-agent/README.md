# Observability Agent

Spring Boot service exposing REST APIs and MCP tools for Loki logs and Prometheus metrics.

Build: `mvn -f microservices/observability-agent clean package`

## Swagger UI

After the service is running, open Swagger UI to try the APIs:

1. Port-forward (service is ClusterIP in Kubernetes):

   ```powershell
   kubectl port-forward -n observability svc/observability-agent 8091:8091
   ```

2. Open in a browser:

   - Swagger UI: `http://localhost:8091/swagger-ui.html`
   - OpenAPI JSON: `http://localhost:8091/v3/api-docs`

Health: `http://localhost:8091/actuator/health`
