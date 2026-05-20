from contextlib import asynccontextmanager

from fastapi import FastAPI

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
app.add_middleware(RequestLoggingMiddleware)
app.add_middleware(CorrelationIdMiddleware)
app.include_router(router)
