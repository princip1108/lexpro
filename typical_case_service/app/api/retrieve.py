import logging

from fastapi import (
    APIRouter,
    HTTPException,
    Request,
)

from app.schemas.retrieve import (
    TypicalRetrieveRequest,
)

logger = logging.getLogger("uvicorn.error")


router = APIRouter(
    prefix="/api/v1/typical-cases",
    tags=["典型案例检索"],
)


@router.post(
    "/retrieve",
    summary="典型案例数据库检索",
)
def retrieve_typical_cases(
    request_data: TypicalRetrieveRequest,
    request: Request,
) -> dict:
    """
    接口2：

    根据接口1返回的analysis_id读取查询向量，
    在core_typical、core_2025、core_2024、
    core_2023四张表中并行检索。

    完整候选结果及embedding会保存到TaskStore，
    返回前端的结果不会包含embedding。
    """
    resources = request.app.state.resources

    retrieval_service = (
        resources.retrieval_service
    )

    if retrieval_service is None:
        raise HTTPException(
            status_code=503,
            detail="检索服务尚未初始化",
        )

    try:
        result = (
            retrieval_service.retrieve_typical_cases(
                request_data
            )
        )

        return {
            "code": 0,
            "message": "success",
            "data": result,
        }

    except KeyError as exc:
        # analysis_id不存在
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
            "典型案例数据库检索失败"
        )

        raise HTTPException(
            status_code=500,
            detail=f"典型案例数据库检索失败：{exc}",
        ) from exc