from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


# typical_case_service/
PROJECT_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    # =========================
    # 服务配置
    # =========================
    APP_NAME: str = "典型案例推送服务"
    APP_VERSION: str = "1.0.0"
    APP_HOST: str = "0.0.0.0"
    APP_PORT: int = 8000
    LOG_LEVEL: str = "INFO"

    # =========================
    # PostgreSQL配置
    # =========================
    DATABASE_URL: str = (
        "postgresql+psycopg://postgres:root@localhost:5432/law_db"
    )

    DB_POOL_SIZE: int = 10
    DB_MAX_OVERFLOW: int = 10
    DB_POOL_RECYCLE: int = 1800

    # =========================
    # 模型相对路径
    # =========================
    DELTA_MODEL_PATH: str = "models/DELTA_CH/DELTA_CH"
    QWEN_MODEL_PATH: str = "models/Qwen3-14B"
    MLP_WEIGHT_PATH: str = "weights/focus_mlp.pt"

    # =========================
    # 设备配置
    # =========================
    DELTA_DEVICE: str = "cuda:0"
    QWEN_DEVICE: str = "cuda:0"
    MLP_DEVICE: str = "cuda:0"

    DELTA_MAX_LENGTH: int = 512
    DELTA_BATCH_SIZE: int = 16
    QWEN_MAX_NEW_TOKENS: int = 1024

    # =========================
    # 检索配置
    # =========================
    RETRIEVE_PER_TABLE: int = 100
    FINAL_TOP_K: int = 100
    HNSW_EF_SEARCH: int = 200

    # Qwen配置
    # =========================
    QWEN_MODEL_PATH: str = "models/Qwen/Qwen3-14B"
    QWEN_DEVICE: str = "cuda:0"

    QWEN_MAX_SEQ_LENGTH: int = 32768
    QWEN_MAX_NEW_TOKENS: int = 2048
    QWEN_TEMPERATURE: float = 0.1
    QWEN_TOP_P: float = 0.95
    QWEN_REPETITION_PENALTY: float = 1.05
    QWEN_LOAD_IN_4BIT: bool = True



    # =========================
    # 融合配置
    # =========================
    FACT_WEIGHT: float = 1
    FOCUS_WEIGHT: float = 0.4

    model_config = SettingsConfigDict(
        env_file=PROJECT_ROOT / ".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    @staticmethod
    def resolve_project_path(path_value: str) -> Path:
        """将相对路径转换为项目根目录下的绝对路径。"""
        path = Path(path_value)

        if path.is_absolute():
            return path

        return PROJECT_ROOT / path

    @property
    def delta_model_path(self) -> Path:
        return self.resolve_project_path(self.DELTA_MODEL_PATH)

    @property
    def qwen_model_path(self) -> Path:
        return self.resolve_project_path(self.QWEN_MODEL_PATH)

    @property
    def mlp_weight_path(self) -> Path:
        return self.resolve_project_path(self.MLP_WEIGHT_PATH)


settings = Settings()