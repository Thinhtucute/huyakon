from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    hf_token: str
    analyzer_space_url: str = "https://thinhtucute-huyakon-magiv3.hf.space"
    translator_space_url: str = "https://thinhtucute-huyakon-translation.hf.space"
    request_timeout_seconds: float = 180.0
    cors_origins: str = "*"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    @property
    def analyzer_call_url(self) -> str:
        return f"{self.analyzer_space_url.rstrip('/')}/gradio_api/call/analyze_b64"

    @property
    def translator_call_url(self) -> str:
        return f"{self.translator_space_url.rstrip('/')}/gradio_api/call/translate_script"

    @property
    def allowed_origins(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
