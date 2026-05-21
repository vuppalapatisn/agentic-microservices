from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app.api.routes import router
from app.config.settings import get_settings
from app.logging.json_logger import get_logger
from app.middleware.correlation import CorrelationIdMiddleware
from app.middleware.request_logging import RequestLoggingMiddleware
from app.mcp.observability_client import ObservabilityAgentClient


logger = get_logger("talk-to-observability-agent")


@asynccontextmanager
async def lifespan(_: FastAPI):
    settings = get_settings()
    client = ObservabilityAgentClient(settings)
    await client.validate_dependencies()
    logger.info(
        "startup_validation_complete",
        extra={
            "service": "talk-to-observability-agent",
            "observabilityAgentBaseUrl": settings.observability_agent_base_url,
        },
    )
    yield
    await client.aclose()


app = FastAPI(title="Talk To Observability Agent", version="0.0.1", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:8092",
        "http://127.0.0.1:8092",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(RequestLoggingMiddleware)
app.add_middleware(CorrelationIdMiddleware)
app.include_router(router)

_STATIC_DIR = Path(__file__).resolve().parent.parent / "static"
if _STATIC_DIR.is_dir():
    app.mount("/assets", StaticFiles(directory=_STATIC_DIR / "assets"), name="assets")

    @app.get("/", include_in_schema=False)
    async def chat_ui() -> FileResponse:
        return FileResponse(_STATIC_DIR / "index.html")

    @app.get("/{full_path:path}", include_in_schema=False)
    async def chat_ui_fallback(full_path: str) -> FileResponse:
        if full_path.startswith(("api/", "docs", "redoc", "openapi")) or full_path == "health":
            from fastapi import HTTPException

            raise HTTPException(status_code=404)
        candidate = _STATIC_DIR / full_path
        if candidate.is_file():
            return FileResponse(candidate)
        return FileResponse(_STATIC_DIR / "index.html")
