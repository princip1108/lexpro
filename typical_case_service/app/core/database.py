import logging

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

from app.core.config import settings


logger = logging.getLogger(__name__)


def create_database_engine() -> Engine:
    """
    创建数据库引擎和连接池。

    该函数只在服务启动时调用一次。
    """
    logger.info("正在创建PostgreSQL连接池")

    engine = create_engine(
        settings.DATABASE_URL,
        pool_size=settings.DB_POOL_SIZE,
        max_overflow=settings.DB_MAX_OVERFLOW,
        pool_pre_ping=True,
        pool_recycle=settings.DB_POOL_RECYCLE,
    )

    logger.info("PostgreSQL连接池创建完成")
    return engine


def verify_database_connection(engine: Engine) -> None:
    """
    服务启动时验证数据库能否连接。

    如果连接失败，直接抛出异常，阻止服务继续启动。
    """
    logger.info("正在验证PostgreSQL连接")

    with engine.connect() as connection:
        connection.execute(text("SELECT 1"))

    logger.info("PostgreSQL连接验证成功")


def ping_database(engine: Engine | None) -> bool:
    """
    健康检查接口使用。
    """
    if engine is None:
        return False

    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
        return True

    except Exception:
        logger.exception("PostgreSQL健康检查失败")
        return False


def close_database_engine(engine: Engine | None) -> None:
    """
    服务关闭时释放数据库连接池。
    """
    if engine is None:
        return

    logger.info("正在关闭PostgreSQL连接池")
    engine.dispose()
    logger.info("PostgreSQL连接池已关闭")