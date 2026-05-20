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
