import json


def build_reasoning_messages(payload: dict) -> list[dict]:
    return [
        {
            "role": "system",
            "content": (
                "You are an observability assistant. "
                "Use only the structured findings provided. "
                "Respond concisely with a probable root cause and supporting evidence."
            ),
        },
        {
            "role": "user",
            "content": json.dumps(payload, indent=2),
        },
    ]
