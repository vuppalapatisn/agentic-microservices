# Testing the Application

Covers both a local Docker Desktop deployment (`./start.sh`) and an IBM Cloud
Kubernetes Service deployment (`./deploy-ibm-cloud.sh` + the manual rollout
steps in [run.md](run.md)). Commands are the same either way except for how
you reach the services (see "Reaching the services" below).

To run all of the commands below in one shot and capture the real output to
`result.md`, use [capture-test-results.sh](capture-test-results.sh):

```bash
./capture-test-results.sh
```

It runs against whatever cluster your current `kubectl` context points to,
writes `result.md` in the repo root, and cleans up its port-forwards on
exit. Review `result.md` before committing it.

## 1. Unit tests (Java services)

Run before/independent of any deployment — these don't need a cluster.

```bash
cd microservices/product && mvn test && cd ../..
cd microservices/images && mvn test && cd ../..
cd microservices/ecommerce && mvn test && cd ../..
cd microservices/observability-server && mvn test && cd ../..
```

## 2. Cluster smoke checks

```bash
kubectl get pods -n ecommerce
kubectl get pods -n observability
kubectl get svc -n ecommerce
kubectl get svc -n observability

# If any pod isn't Running/Ready:
kubectl describe pod <pod-name> -n <namespace>
kubectl logs -n ecommerce deploy/product
kubectl logs -n ecommerce deploy/images
kubectl logs -n ecommerce deploy/ecommerce
kubectl logs -n observability deploy/observability-server
kubectl logs -n observability deploy/observability-debug-agent
```

## 3. Reaching the services

**Local Docker Desktop**: ClusterIP and LoadBalancer services are reachable
directly on `localhost` at their service port — no port-forward needed.

**IBM Cloud (or any remote cluster)**: your Mac has no direct route to the
cluster's internal network, so use `kubectl port-forward` for every service.
Note: [k8s/ingress/ingress.yaml](k8s/ingress/ingress.yaml) is written for the
AWS ALB ingress controller (`kubernetes.io/ingress.class: alb`) — on IBM
Cloud no controller matches that class, so `kubectl get ingress -n ecommerce`
will show no `ADDRESS` and the `/ecommerceApp`, `/product-service`,
`/image-service` ingress paths won't resolve. Use port-forward instead of
the ingress for testing on IBM Cloud.

```bash
kubectl port-forward -n ecommerce svc/product-service 8081:8090 &
kubectl port-forward -n ecommerce svc/images-service 8082:8090 &
kubectl port-forward -n ecommerce svc/ecommerce-service 8083:8090 &
kubectl port-forward -n observability svc/observability-server 8091:8091 &
kubectl port-forward -n observability svc/observability-debug-agent 8092:8092 &
kubectl port-forward -n observability svc/grafana 3000:3000 &
kubectl port-forward -n observability svc/prometheus 9090:9090 &
```

(On local Docker Desktop, replace `8081`/`8082`/`8083` above with `8090` to
match the localhost URLs used below, or just skip port-forwarding entirely
and hit `localhost:8090` directly for each.)

## 4. Endpoint tests

Each Java service has its actuator health check at the service **root**
(no context-path prefix), and its business endpoints **under** its
context-path (`/product-service`, `/image-service`, `/ecommerce-service`).

```bash
# Health checks
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health

# product-service
curl http://localhost:8081/product-service/products | json_pp

# image-service
curl http://localhost:8082/image-service/images | json_pp

# ecommerce-service (calls product-service + images-service internally —
# a 200 here confirms the whole chain is wired up correctly)
curl http://localhost:8083/ecommerce-service/ecommerceProducts | json_pp

# ecommerce-service: apply a coupon (plain text body, exactly 6 alphanumeric chars)
curl -X POST http://localhost:8083/ecommerce-service/apply-coupon \
  -H "Content-Type: text/plain" \
  -d "ABC123"
```

Via the local Docker Desktop ingress instead of port-forwarding (matches
[microservices/test-curl.sh](microservices/test-curl.sh); does **not** work
on IBM Cloud — see the ingress caveat above):

```bash
curl http://localhost/ecommerceApp/ecommerce-service/ecommerceProducts | json_pp
```

## 5. Observability stack

```bash
# observability-server (Spring Boot + MCP server)
curl http://localhost:8091/api/observability/services | json_pp
open http://localhost:8091/swagger-ui.html        # Swagger UI

# observability-debug-agent (FastAPI + chat UI)
curl http://localhost:8092/health
open http://localhost:8092                        # chatbot UI
open http://localhost:8092/docs                   # FastAPI Swagger

# Grafana — default login is admin / admin (forces a password change on first login)
open http://localhost:3000

# Prometheus
open http://localhost:9090
```

## 6. Cleanup

If you used `kubectl port-forward` in the background (the `&` in step 3),
stop them when done:

```bash
jobs -l              # list the backgrounded port-forward processes
kill %1 %2 %3 %4 %5 %6 %7    # or kill the specific job numbers shown above
```
