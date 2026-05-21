# Talk To Observability Agent

FastAPI + LangGraph service that investigates observability questions using the existing `observability-agent`.

## API

- `POST /api/v1/investigate` — run an investigation
- `GET /health` — health check

## Chat UI (React + Vite + TypeScript)

A browser chatbot calls the investigate API and shows summary, evidence, and Grafana links.

**Run with the rest of the stack:** `start.bat` from the repo root, then open http://localhost:8092. See **[chatbot-ui-readme.md](../../chatbot-ui-readme.md)** for full steps and troubleshooting.

### Local development

Terminal 1 — API (port 8092):

```bash
cd microservices/talk-to-observability-agent
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8092
```

Terminal 2 — UI (port 5173, proxies `/api` to 8092):

```bash
cd microservices/talk-to-observability-agent/ui
npm install
npm run dev
```

Open http://localhost:5173

### Production (UI bundled in Docker image)

After `docker build`, open http://localhost:8092 for the chat UI and API on the same port.

```bash
cd microservices/talk-to-observability-agent
docker build -t talk-to-observability-agent .
```

Rebuild and redeploy the Kubernetes deployment to use the new image.
