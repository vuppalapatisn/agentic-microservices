import json

from app.prompts.error_logs import build_error_log_messages


def build_heap_percent_messages(payload: dict) -> list[dict]:
    return [
        {
            "role": "system",
            "content": (
                "You are an observability assistant. Use only the JSON findings provided. "
                "Answer with JVM heap usage as a percentage first, then used and max sizes exactly as given. "
                "Do not reference logs or request rates. Be concise (2-4 sentences)."
            ),
        },
        {"role": "user", "content": json.dumps(payload, indent=2)},
    ]


def build_reasoning_messages(payload: dict, mode: str = "default") -> list[dict]:
    if mode == "error_logs":
        return build_error_log_messages(payload)
    if mode == "heap_percent":
        return build_heap_percent_messages(payload)
    return [
        {
            "role": "system",
            "content": (
                "You are an observability assistant. "
                "Use only the structured findings provided. "
                "Keep heap/memory sizes exactly as given (MB or GB). "
                "Keep request rates exactly as given (rps). "
                "Respond concisely with a probable root cause and supporting evidence."
            ),
        },
        {
            "role": "user",
            "content": json.dumps(payload, indent=2),
        },
    ]
