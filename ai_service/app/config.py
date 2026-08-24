from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        env_prefix="AI_SERVICE_",
        extra="ignore",
    )

    environment: str = "development"
    internal_api_key: SecretStr = SecretStr("")

    llm_enabled: bool = False
    llm_base_url: str = "https://api.groq.com/openai/v1"
    llm_api_key: SecretStr = SecretStr("")

    speech_enabled: bool = False
    azure_speech_base_url: str = "https://example.cognitiveservices.azure.com"
    azure_speech_api_key: SecretStr = SecretStr("")

    connect_timeout_seconds: float = 5.0
    read_timeout_seconds: float = 45.0
    max_audio_bytes: int = 10_485_760


@lru_cache
def get_settings() -> Settings:
    return Settings()
