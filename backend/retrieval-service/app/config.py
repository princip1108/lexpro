from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="LEXPRO_RETRIEVAL_", extra="ignore")

    database_url: str
    model_name: str = "BAAI/bge-m3"
    model_revision: str = "main"
    dimension: int = 1024
    distance: str = "cosine"
    max_candidates: int = Field(default=1000, ge=50, le=5000)
    default_limit: int = Field(default=10, ge=1, le=50)


@lru_cache
def get_settings() -> Settings:
    return Settings()
