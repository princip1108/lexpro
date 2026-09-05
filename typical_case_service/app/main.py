import os
os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request

from app.core.config import settings
from app.core.database import ping_database
from app.core.resources import AppResources
from app.api.analyze import router as analyze_router
from app.api.retrieve import router as retrieve_router
from app.api.rerank import router as rerank_router
from app.api.recommend import (
    router as recommend_router,
)


logging.basicConfig(
    level=getattr(
        logging,
        settings.LOG_LEVEL.upper(),
        logging.INFO,
    ),
    format=(
        "%(asctime)s | "
        "%(levelname)s | "
        "%(name)s | "
        "%(message)s"
    ),
)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    FastAPI生命周期。

    yield之前：
        加载数据库、模型、权重。

    yield之后：
        关闭数据库连接池并释放模型资源。
    """
    resources = AppResources()

    try:
        resources.load_all()
        app.state.resources = resources

        logger.info("%s启动成功", settings.APP_NAME)

        yield

    finally:
        resources.close_all()
        logger.info("%s已关闭", settings.APP_NAME)


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="典型案例智能推送接口服务",
    lifespan=lifespan,
)

app.include_router(analyze_router)

app.include_router(retrieve_router)

app.include_router(rerank_router)

app.include_router(recommend_router)
app.include_router(recommend_router)
@app.get("/")
def root():
    return {
        "code": 0,
        "message": settings.APP_NAME,
        "version": settings.APP_VERSION,
    }


@app.get("/health")
def health_check(request: Request):
    resources: AppResources = request.app.state.resources

    database_ready = ping_database(
        resources.db_engine
    )

    service_ready = (
        resources.ready
        and database_ready
    )

    return {
        "code": 0 if service_ready else 1,
        "message": (
            "service is ready"
            if service_ready
            else "service is not ready"
        ),
        "data": {
            "service_ready": service_ready,
            "database_ready": database_ready,
            "delta_loaded": (
                resources.delta_engine is not None
            ),
            "qwen_loaded": (
                resources.qwen_engine is not None
            ),
            "mlp_loaded": (
                resources.mlp_engine is not None
            ),
        },
    }