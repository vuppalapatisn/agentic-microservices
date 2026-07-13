from openai import AsyncOpenAI, OpenAIError

from app.config.settings import Settings


class ReasoningService:
    def __init__(self, settings: Settings) -> None:
        # base_url lets the agent target any OpenAI-compatible endpoint (e.g. Gemini's
        # /v1beta/openai/). Empty base_url falls back to the OpenAI default.
        self.client = AsyncOpenAI(
            api_key=settings.openai_api_key,
            base_url=settings.openai_base_url or None,
        )
        self.model = settings.openai_model

    async def summarize(self, messages: list[dict]) -> str:
        try:
            response = await self.client.chat.completions.create(
                model=self.model,
                messages=messages,
                max_tokens=220,
            )
            return response.choices[0].message.content.strip()
        except OpenAIError as exc:
            raise RuntimeError(f"OpenAI reasoning failed: {exc}") from exc
