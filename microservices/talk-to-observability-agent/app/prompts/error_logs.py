import json


def build_error_log_messages(payload: dict) -> list[dict]:
    return [
        {
            "role": "system",
            "content": (
                "You are an observability assistant. Use only the JSON findings provided. "
                "Explain the error and root cause for the given correlation id. "
                "Include HTTP/status hints, downstream service name, and stack trace excerpt from log messages. "
                "Do not invent metrics. If logs show coupon-service, 404, or unreachable host, state that clearly. "
                "Be concise (3-6 sentences)."
            ),
        },
        {"role": "user", "content": json.dumps(payload, indent=2)},
    ]
