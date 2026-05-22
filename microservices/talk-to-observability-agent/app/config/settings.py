import os
from functools import lru_cache


class Settings:
    def __init__(self) -> None:
        self.openai_api_key = os.getenv("OPENAI_API_KEY", "").strip()
        self.openai_model = os.getenv("OPENAI_MODEL", "gpt-4.1-mini").strip()
        self.observability_agent_base_url = os.getenv(
            "OBSERVABILITY_AGENT_BASE_URL",
            "http://observability-server.observability.svc.cluster.local:8091",
        ).strip()
        self.request_timeout_seconds = int(os.getenv("REQUEST_TIMEOUT_SECONDS", "10"))
        self.startup_validation_retries = int(os.getenv("STARTUP_VALIDATION_RETRIES", "30"))
        self.startup_validation_retry_seconds = float(
            os.getenv("STARTUP_VALIDATION_RETRY_SECONDS", "2")
        )
        self.grafana_base_url = os.getenv("GRAFANA_BASE_URL", "http://localhost:3000").strip()
        self.grafana_api_base_url = os.getenv(
            "GRAFANA_API_BASE_URL",
            "http://grafana.observability.svc.cluster.local:3000",
        ).strip()
        self.grafana_loki_datasource_uid = os.getenv("GRAFANA_LOKI_DATASOURCE_UID", "loki").strip()
        self.grafana_dashboard_uid = os.getenv(
            "GRAFANA_DASHBOARD_UID", "ecommerce-observability"
        ).strip()
        self.langgraph_debug = os.getenv("LANGGRAPH_DEBUG", "true").strip().lower() in (
            "1",
            "true",
            "yes",
        )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
