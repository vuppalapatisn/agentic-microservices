# Observability Chatbot UI

Browser chat for **talk-to-observability-agent** (React + Vite + TypeScript). The UI is built into the service Docker image and served on the same port as the API.

## Quick start (recommended)

After prerequisites and the one-time OpenAI secret (see [README.md](README.md)):

```powershell
cd C:\git\microservices-ecommerce-2
start.bat
```

When startup finishes, open:

| URL | Purpose |
|-----|---------|
| http://localhost:8092 | Chat UI |
| http://localhost:8092/docs | API Swagger |
| http://localhost:8092/health | Health check |

No separate `npm` step is required for this path — `start.bat` builds the UI inside the `talk-to-observability-agent` Docker image.

## One-time secret

```powershell
kubectl create secret generic talk-to-observability-agent-secret `
  --from-literal=OPENAI_API_KEY=your-key-here `
  -n observability
```

## Using the chat

1. Open http://localhost:8092
2. Type a question (e.g. *Why is ecommerce slow in the last 15 minutes?*) or use a suggestion chip
3. Optionally paste a **correlation ID** from the traffic script into the correlation field
4. Read the summary, evidence, probable root cause, and Grafana links in the reply

**Example with correlation ID** (from `scripts/simulate_traffic_spike.py`):

- Query: `Find reason for slowness for correlation id <uuid>`
- Or set the correlation ID field and ask: `slow request last 30 minutes`

## Generate traffic for demos

```powershell
pip install -r scripts/requirements.txt
python scripts/simulate_traffic_spike.py
```

Use the printed `correlationId` in the chat or in Grafana Explore (Loki).

## Local UI development (optional)

Use this when changing files under `microservices/talk-to-observability-agent/ui/` without rebuilding the full cluster.

**Terminal 1 — API** (needs observability stack + port-forward or cluster API):

```powershell
cd microservices\talk-to-observability-agent
pip install -r requirements.txt
$env:OBSERVABILITY_AGENT_BASE_URL = "http://localhost:8091"
$env:OPENAI_API_KEY = "your-key"
uvicorn app.main:app --reload --port 8092
```

Port-forward observability-server if not exposed on localhost:

```powershell
kubectl port-forward -n observability svc/observability-server 8091:8091
```

**Terminal 2 — Vite dev server** (proxies `/api` to 8092):

```powershell
cd microservices\talk-to-observability-agent\ui
npm install
npm run dev
```

Open http://localhost:5173

Requires [Node.js](https://nodejs.org/) (LTS) on your PATH.

## Rebuild only the chat service

```powershell
cd microservices\talk-to-observability-agent
$tag = Get-Date -Format "yyyyMMddHHmmss"
docker build --no-cache -t "talk-to-observability-agent:$tag" .
kubectl set image deployment/talk-to-observability-agent talk-to-observability-agent="talk-to-observability-agent:$tag" -n observability
kubectl rollout status deployment/talk-to-observability-agent -n observability --timeout=120s
```

## Troubleshooting

| Symptom | What to check |
|---------|----------------|
| Blank page at :8092 | Pod logs: `kubectl logs -n observability deploy/talk-to-observability-agent` — image must include `static/` from UI build |
| CrashLoop `observability-server is unavailable during startup validation` | Startup race: rebuild talk-to image (retries up to ~60s) or ensure observability-server pod is Running first |
| 503 / error in chat | `OPENAI_API_KEY` secret in `observability`; observability-server and Loki/Prometheus healthy |
| Grafana links 404 | Grafana at http://localhost:3000; configmap `GRAFANA_BASE_URL` is browser URL, `GRAFANA_API_BASE_URL` is in-cluster |
| `npm` not found (local dev) | Install Node.js LTS; restart terminal |

## Source layout

- UI: `microservices/talk-to-observability-agent/ui/`
- API + static serving: `microservices/talk-to-observability-agent/app/main.py`
- Multi-stage Docker build: `microservices/talk-to-observability-agent/Dockerfile`
