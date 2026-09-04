from functools import lru_cache

from pydantic import AnyHttpUrl, Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        env_prefix="AI_SERVICE_",
        extra="ignore",
    )

    environment: str = Field(default="development", pattern="^(development|test|production)$")
    internal_api_key: SecretStr = SecretStr("")

    llm_enabled: bool = False
    llm_base_url: AnyHttpUrl = AnyHttpUrl("https://api.groq.com/openai/v1")
    llm_api_key: SecretStr = SecretStr("")

    speech_enabled: bool = False
    azure_speech_base_url: AnyHttpUrl = AnyHttpUrl("https://example.cognitiveservices.azure.com")
    azure_speech_api_key: SecretStr = SecretStr("")

    connect_timeout_seconds: float = Field(default=5.0, gt=0, le=60)
    read_timeout_seconds: float = Field(default=45.0, gt=0, le=300)
    max_audio_bytes: int = Field(default=10_485_760, ge=1, le=52_428_800)


@lru_cache
def get_settings() -> Settings:
    return Settings()
