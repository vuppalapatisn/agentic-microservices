from __future__ import annotations

MONITORING_KEYWORDS = (
    "slow",
    "slowness",
    "latency",
    "timeout",
    "heap",
    "memory",
    "thread",
    "rate",
    "rps",
    "traffic",
    "load",
    "metric",
    "metrics",
    "prometheus",
    "saturation",
    "overload",
    "spike",
)

LOG_ERROR_KEYWORDS = (
    "error",
    "fail",
    "failure",
    "exception",
    "stack",
    "trace",
    "log",
    "logs",
    "404",
    "500",
    "502",
    "coupon",
    "details",
)

INVESTIGATION_KEYWORDS = (
    "slow",
    "slowness",
    "latency",
    "timeout",
    "correlation",
    "request id",
    "requestid",
)

HEAP_USAGE_KEYWORDS = ("usage", "used", "percent", "%", "how much")


def _matches_any(text: str, keywords: tuple[str, ...]) -> bool:
    return any(keyword in text for keyword in keywords)


def classify_investigation(query: str) -> dict[str, bool]:
    lowered = query.lower()
    needs_monitoring = _matches_any(lowered, MONITORING_KEYWORDS)
    needs_logs = _matches_any(lowered, LOG_ERROR_KEYWORDS) or _matches_any(
        lowered, INVESTIGATION_KEYWORDS
    )
    heap_usage_percent_query = (
        needs_monitoring
        and _matches_any(lowered, ("heap", "memory"))
        and _matches_any(lowered, HEAP_USAGE_KEYWORDS)
        and not _matches_any(lowered, INVESTIGATION_KEYWORDS)
        and not _matches_any(lowered, LOG_ERROR_KEYWORDS)
    )
    if not needs_logs and not needs_monitoring:
        needs_logs = True
        needs_monitoring = True

    fetch_logs = needs_logs
    fetch_error_logs = needs_logs
    if heap_usage_percent_query:
        fetch_heap_metrics = True
        fetch_heap_max_metrics = True
        fetch_thread_metrics = False
        fetch_request_rate = False
    elif needs_monitoring:
        fetch_heap_metrics = True
        fetch_heap_max_metrics = False
        fetch_thread_metrics = True
        fetch_request_rate = True
    else:
        fetch_heap_metrics = False
        fetch_heap_max_metrics = False
        fetch_thread_metrics = False
        fetch_request_rate = False

    return {
        "needs_logs": needs_logs,
        "needs_monitoring": needs_monitoring,
        "heap_usage_percent_query": heap_usage_percent_query,
        "fetch_logs": fetch_logs,
        "fetch_error_logs": fetch_error_logs,
        "fetch_heap_metrics": fetch_heap_metrics,
        "fetch_heap_max_metrics": fetch_heap_max_metrics,
        "fetch_thread_metrics": fetch_thread_metrics,
        "fetch_request_rate": fetch_request_rate,
    }
