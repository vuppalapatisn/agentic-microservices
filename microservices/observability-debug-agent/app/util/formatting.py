def format_bytes(value: float) -> str:
    """Format Prometheus byte values (e.g. jvm_memory_used_bytes) for human-readable output."""
    if value < 0:
        value = 0.0
    gb = 1024**3
    mb = 1024**2
    kb = 1024
    if value >= gb:
        return f"{value / gb:.2f} GB"
    if value >= mb:
        return f"{value / mb:.2f} MB"
    if value >= kb:
        return f"{value / kb:.2f} KB"
    return f"{value:.0f} B"


def format_rps(value: float) -> str:
    """Format Prometheus request-rate values (requests per second) for human-readable output."""
    if value < 0:
        value = 0.0
    if value >= 100:
        return f"{value:.0f} rps"
    if value >= 10:
        return f"{value:.1f} rps"
    return f"{value:.2f} rps"


def format_percent(value: float) -> str:
    if value < 0:
        value = 0.0
    return f"{value:.1f}%"


def format_count(value: float) -> str:
    """Whole-number gauges (e.g. jvm_threads_live_threads)."""
    if value < 0:
        value = 0.0
    return str(int(round(value)))
