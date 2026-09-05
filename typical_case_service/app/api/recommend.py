import logging
from typing import Any

from fastapi import (
    APIRouter,
    HTTPException,
    Request,
)

from app.schemas.recommend import (
    TypicalCaseSearchRequest,
)


logger = logging.getLogger(
    "uvicorn.error"
)


router = APIRouter(
    prefix="/api/v1/typical-cases",
    tags=["典型案例最终检索"],
)


@router.post(
    "/search",
    summary="典型案例一键检索",
)
def search_typical_cases(
    request_data: TypicalCaseSearchRequest,
    request: Request,
) -> dict[str, Any]:
    """
    前端展示完接口1的案件分析结果后，
    调用本接口完成最终典型案例检索。

    后端自动执行：
    数据库召回 → MLP融合重排序 → 正文读取。
    """
    resources = getattr(
        request.app.state,
        "resources",
        None,
    )

    if resources is None:
        raise HTTPException(
            status_code=503,
            detail="服务资源尚未初始化",
        )

    recommend_service = (
        resources.recommend_service
    )

    if recommend_service is None:
        raise HTTPException(
            status_code=503,
            detail="典型案例检索服务尚未初始化",
        )

    try:
        result = (
            recommend_service
            .search_typical_cases(
                request_data
            )
        )

        return {
            "code": 0,
            "message": "success",
            "data": result,
        }

    except KeyError as exc:
        raise HTTPException(
            status_code=404,
            detail=str(exc),
        ) from exc

    except ValueError as exc:
        raise HTTPException(
            status_code=400,
            detail=str(exc),
        ) from exc

    except Exception as exc:
        logger.exception(
            "典型案例最终检索失败"
        )

        raise HTTPException(
            status_code=500,
            detail=(
                "典型案例最终检索失败："
                f"{exc}"
            ),
        ) from exc