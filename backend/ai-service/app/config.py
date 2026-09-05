from functools import lru_cache

from pydantic import Field, HttpUrl, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="LEXPRO_AI_SERVICE_", extra="ignore")

    internal_token: SecretStr = Field(min_length=32)
    mineru_base_url: HttpUrl = HttpUrl("http://127.0.0.1:13456")
    mineru_version: str = "mineru-2.7.1/MinerU2.5-2509-1.2B"
    lexpro_base_url: HttpUrl = HttpUrl("http://127.0.0.1:8001")
    lexpro_model_name: str = "LexPro_8B"
    lexpro_model_version: str = "LexPro_8B"
    connect_timeout_seconds: float = Field(default=5.0, gt=0, le=60)
    mineru_read_timeout_seconds: float = Field(default=900.0, gt=0, le=3600)
    lexpro_read_timeout_seconds: float = Field(default=900.0, gt=0, le=3600)
    max_file_bytes: int = Field(default=50 * 1024 * 1024, ge=1, le=200 * 1024 * 1024)
    batch_size: int = Field(default=128, ge=1, le=256)


@lru_cache
def get_settings() -> Settings:
    return Settings()
