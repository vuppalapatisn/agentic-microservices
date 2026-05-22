from openai import AsyncOpenAI, OpenAIError

from app.config.settings import Settings


class ReasoningService:
    def __init__(self, settings: Settings) -> None:
        self.client = AsyncOpenAI(api_key=settings.openai_api_key)
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
