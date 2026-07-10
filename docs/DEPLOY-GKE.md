# Deploying to Google Cloud (GKE)

Step-by-step runbook for running the full `agentic-microservices` stack on a
Google Kubernetes Engine cluster. This mirrors [deploy-ibm-cloud.sh](../deploy-ibm-cloud.sh)
but adapts the two things that are cloud-specific: the **ingress** and the **image pull**.

> New to free options first? See [FREE-TESTING-DEPLOYMENT.md](FREE-TESTING-DEPLOYMENT.md).
> Architecture + service list: [/CLAUDE.md](../CLAUDE.md).

---

## Two GKE-specific gotchas (read first)

1. **Use GKE _Standard_, not Autopilot.** `promtail` is a DaemonSet that mounts
   host paths (`/var/log/pods`, `/run/promtail` — see
   `k8s/observability/promtail/daemonset.yaml`). GKE Autopilot **blocks `hostPath`**,
   so promtail (log shipping) would fail there. Standard clusters allow it.

2. **The bundled ingress is AWS-only.** `k8s/ingress/ingress.yaml` and
   `k8s/observability/ingress.yaml` use `ingressClassName: alb` with
   `alb.ingress.kubernetes.io/*` annotations — those are AWS Load Balancer Controller
   annotations and do nothing on GKE. Do **not** apply those two files on GKE. Instead
   install **ingress-nginx** and apply the replacement ingress in Step 7 below.

Everything else in `k8s/` applies unchanged.

---

## Prerequisites

- A Google Cloud account with billing enabled (a new account gets **$300 / 90-day**
  free credit — enough for a bounded test).
- `gcloud` CLI installed and authenticated: `gcloud auth login`.
- `kubectl` (install via `gcloud components install kubectl`).
- Helm (for ingress-nginx): https://helm.sh/docs/intro/install/
- Your `OPENAI_API_KEY` (required for `/api/v1/investigate`; a real cost, not free).

---

## Step 1 — Project and APIs

```bash
# Pick or create a project, then set it as default
gcloud projects create agentic-demo-$RANDOM --name="agentic-microservices"   # or use an existing one
gcloud config set project <YOUR_PROJECT_ID>

# Enable the APIs this deploy needs
gcloud services enable container.googleapis.com          # GKE
gcloud services enable artifactregistry.googleapis.com   # only if building images (Step 5, Option B)
```

## Step 2 — Create the GKE Standard cluster

The stack requests ~1 vCPU / 2.4 GB and peaks ~3.4 vCPU / 4.75 GB. A single
`e2-standard-4` node (4 vCPU / 16 GB) runs it comfortably; two `e2-standard-2`
(2 vCPU / 8 GB each) also work.

```bash
gcloud container clusters create agentic-microservices \
  --zone=us-central1-a \
  --num-nodes=1 \
  --machine-type=e2-standard-4 \
  --release-channel=regular
```

> Zonal (single-zone) clusters get a free control plane; you pay only for the node VM,
> which the trial credit covers.

## Step 3 — Point kubectl at the cluster

```bash
gcloud container clusters get-credentials agentic-microservices --zone=us-central1-a
kubectl get nodes        # confirm the node is Ready
```

## Step 4 — Namespaces

```bash
kubectl apply -f k8s/namespace.yaml                 # ecommerce
kubectl apply -f k8s/observability/namespace.yaml   # observability
```

## Step 5 — Make the images pullable

The deployments reference `docker.io/sudhavuppalapati/*` images and list an
`imagePullSecrets: dockerhub-registry-secret`. Pick one option.

### Option A — Reuse the existing public Docker Hub images (fastest, no build)

If those Docker Hub repos are public, GKE nodes can pull them, but the deployments
still name a pull secret — create a (harmless) one in both namespaces so the kubelet
doesn't warn:

```bash
for ns in ecommerce observability; do
  kubectl create secret docker-registry dockerhub-registry-secret \
    --docker-server=https://index.docker.io/v1/ \
    --docker-username=<DOCKERHUB_USERNAME> \
    --docker-password=<DOCKERHUB_TOKEN> \
    --docker-email=<EMAIL> \
    --namespace="$ns"
done
```

### Option B — Build and push to Google Artifact Registry (self-owned images)

```bash
# One-time: create a Docker repo and let Docker auth to it
gcloud artifacts repositories create agentic \
  --repository-format=docker --location=us-central1
gcloud auth configure-docker us-central1-docker.pkg.dev

# Build + push each image (Java services need mvn first)
REG=us-central1-docker.pkg.dev/<YOUR_PROJECT_ID>/agentic
for svc in ecommerce product images observability-server; do
  (cd microservices/$svc && mvn clean package -DskipTests && \
   docker build -t $REG/$svc:v1 . && docker push $REG/$svc:v1)
done
(cd microservices/observability-debug-agent && \
 docker build -t $REG/observability-debug-agent-java:v1 . && \
 docker push $REG/observability-debug-agent-java:v1)
```

Then repoint each deployment to the new registry (GKE pulls from Artifact Registry in
the same project with no pull secret needed):

```bash
kubectl set image deployment/ecommerce ecommerce=$REG/ecommerce:v1 -n ecommerce
kubectl set image deployment/product product=$REG/product:v1 -n ecommerce
kubectl set image deployment/images images=$REG/images:v1 -n ecommerce
kubectl set image deployment/observability-server observability-server=$REG/observability-server:v1 -n observability
kubectl set image deployment/observability-debug-agent observability-debug-agent=$REG/observability-debug-agent-java:v1 -n observability
```

(Run these `set image` commands **after** Step 6, once the deployments exist.)

## Step 6 — Apply the app + observability manifests

Same set and order as `deploy-ibm-cloud.sh`, **minus the ALB ingress files**:

```bash
kubectl apply -f k8s/product
kubectl apply -f k8s/images
kubectl apply -f k8s/ecommerce
# NOTE: skip `k8s/ingress` (ALB-only) — handled in Step 7
kubectl apply -f k8s/observability/prometheus
kubectl apply -f k8s/observability/loki
kubectl apply -f k8s/observability/promtail
kubectl apply -f k8s/observability/grafana
kubectl apply -f k8s/observability-server
kubectl apply -f k8s/observability-debug-agent/configmap.yaml
kubectl apply -f k8s/observability-debug-agent/deployment.yaml
kubectl apply -f k8s/observability-debug-agent/service.yaml
# NOTE: skip `k8s/observability/ingress.yaml` (ALB-only) — handled in Step 7
```

Create the OpenAI secret (the deployment references it as `optional: true`, so pods
start without it, but investigations fail until it exists):

```bash
kubectl create secret generic observability-debug-agent-secret \
  --from-literal=OPENAI_API_KEY=<YOUR_OPENAI_KEY> -n observability
kubectl rollout restart deployment/observability-debug-agent -n observability
```

## Step 7 — Ingress on GKE (ingress-nginx)

Install the controller (creates a Google network load balancer with a public IP):

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace
```

Apply this GKE-compatible ingress (replaces both ALB ingress files). Save as
`k8s-gke/ingress-nginx.yaml` or apply from stdin:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ecommerce-ingress
  namespace: ecommerce
spec:
  ingressClassName: nginx
  rules:
    - http:
        paths:
          - path: /ecommerceApp
            pathType: Prefix
            backend: { service: { name: ecommerce-service, port: { number: 8090 } } }
          - path: /product-service
            pathType: Prefix
            backend: { service: { name: product-service, port: { number: 8090 } } }
          - path: /image-service
            pathType: Prefix
            backend: { service: { name: images-service, port: { number: 8090 } } }
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: observability-ingress
  namespace: observability
spec:
  ingressClassName: nginx
  rules:
    - http:
        paths:
          - path: /observability
            pathType: Prefix
            backend: { service: { name: observability-debug-agent, port: { number: 8092 } } }
```

```bash
kubectl apply -f k8s-gke/ingress-nginx.yaml
```

## Step 8 — Verify

```bash
kubectl get pods -n ecommerce
kubectl get pods -n observability
kubectl rollout status deployment/observability-debug-agent -n observability

# External IP of the nginx controller
kubectl get svc ingress-nginx-controller -n ingress-nginx \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}'; echo
```

With `EXTERNAL_IP` from above:

| What | URL |
|------|-----|
| Ecommerce API | `http://EXTERNAL_IP/ecommerceApp/ecommerce-service/ecommerceProducts` |
| Agent chat UI | `http://EXTERNAL_IP/observability` |

Grafana/Prometheus aren't exposed via ingress by design — reach them with
`kubectl port-forward -n observability svc/grafana 3000:3000` when needed.

Then drive an investigation end-to-end (generate traffic, POST `/api/v1/investigate`,
confirm the correlation-id flows through Loki and Grafana links resolve), per
[/CLAUDE.md](../CLAUDE.md) "How to avoid regressions".

## Step 9 — Tear down (so the credit isn't burned)

```bash
gcloud container clusters delete agentic-microservices --zone=us-central1-a
# If you used Option B: optionally delete the Artifact Registry repo
gcloud artifacts repositories delete agentic --location=us-central1
```

Deleting the cluster also removes the load balancer; both bill hourly while they exist.

---

## Notes

- **`GRAFANA_BASE_URL` in the agent configmap is `http://localhost:3000`** — deep links
  the agent returns point at localhost, which only resolves if you port-forward Grafana.
  For a shared GKE URL, expose Grafana (add an nginx ingress rule) and update
  `GRAFANA_BASE_URL` in `k8s/observability-debug-agent/configmap.yaml`. This is a config
  change, not a code change.
- Keep to **immutable image tags** for anything shared (`v1`, a SHA, a timestamp) — not
  `latest` — per [.claude/rules/rules.md](../.claude/rules/rules.md).
- No secrets belong in the repo: the OpenAI key and any registry creds are created with
  `kubectl create secret` at deploy time only.
