import os
from functools import lru_cache


class Settings:
    def __init__(self) -> None:
        self.openai_api_key = os.getenv("OPENAI_API_KEY", "").strip()
        self.openai_model = os.getenv("OPENAI_MODEL", "gpt-4.1-mini").strip()
        self.observability_agent_base_url = os.getenv(
            "OBSERVABILITY_AGENT_BASE_URL",
            "http://observability-agent.observability.svc.cluster.local:8091",
        ).strip()
        self.request_timeout_seconds = int(os.getenv("REQUEST_TIMEOUT_SECONDS", "10"))


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
