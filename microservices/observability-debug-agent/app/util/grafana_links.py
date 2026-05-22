from __future__ import annotations

import json
import logging
from datetime import datetime
from functools import lru_cache
from urllib.parse import quote

import httpx

logger = logging.getLogger(__name__)


def _epoch_ms(dt: datetime) -> int:
    return int(dt.timestamp() * 1000)


@lru_cache(maxsize=4)
def resolve_loki_datasource_uid(base_url: str, fallback_uid: str = "loki") -> str:
    return _resolve_datasource_uid(base_url, "Loki", fallback_uid)


@lru_cache(maxsize=4)
def resolve_dashboard_uid(
    base_url: str,
    dashboard_title: str = "Ecommerce Observability",
    fallback_uid: str = "ecommerce-observability",
) -> str:
    url = f"{base_url.rstrip('/')}/api/search"
    try:
        response = httpx.get(
            url,
            params={"query": dashboard_title, "type": "dash-db"},
            timeout=3.0,
        )
        response.raise_for_status()
        for item in response.json():
            if item.get("title") == dashboard_title and item.get("uid"):
                return item["uid"]
    except Exception as exc:
        logger.warning("grafana_dashboard_uid_lookup_failed error=%s", exc)
    return fallback_uid


def _resolve_datasource_uid(base_url: str, name: str, fallback_uid: str) -> str:
    url = f"{base_url.rstrip('/')}/api/datasources/name/{name}"
    try:
        response = httpx.get(url, timeout=3.0)
        response.raise_for_status()
        uid = response.json().get("uid")
        if uid:
            return uid
    except Exception as exc:
        logger.warning("grafana_datasource_uid_lookup_failed name=%s error=%s", name, exc)
    return fallback_uid


def build_loki_explore_url(
    base_url: str,
    start: datetime,
    end: datetime,
    *,
    correlation_id: str | None = None,
    fallback_loki_uid: str = "loki",
    api_base_url: str | None = None,
) -> str:
    uid = resolve_loki_datasource_uid(api_base_url or base_url, fallback_loki_uid)
    if correlation_id:
        expr = f'{{namespace=~"ecommerce|observability"}} |= "{correlation_id}"'
    else:
        expr = '{namespace=~"ecommerce|observability"}'

    pane = {
        "datasource": uid,
        "queries": [
            {
                "refId": "A",
                "expr": expr,
                "queryType": "range",
                "datasource": {"type": "loki", "uid": uid},
            }
        ],
        "range": {"from": str(_epoch_ms(start)), "to": str(_epoch_ms(end))},
    }
    panes = {"a": pane}
    encoded = quote(json.dumps(panes, separators=(",", ":")), safe="")
    return f"{base_url.rstrip('/')}/explore?orgId=1&panes={encoded}&schemaVersion=1"


def build_dashboard_url(
    base_url: str,
    start: datetime,
    end: datetime,
    *,
    fallback_dashboard_uid: str = "ecommerce-observability",
    api_base_url: str | None = None,
) -> str:
    uid = resolve_dashboard_uid(
        api_base_url or base_url, fallback_uid=fallback_dashboard_uid
    )
    return (
        f"{base_url.rstrip('/')}/d/{uid}"
        f"?from={_epoch_ms(start)}&to={_epoch_ms(end)}&timezone=UTC"
    )
