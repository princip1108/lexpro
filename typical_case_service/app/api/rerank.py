import logging
from typing import Any

from fastapi import (
    APIRouter,
    HTTPException,
    Request,
)

from app.schemas.rerank import RerankRequest


logger = logging.getLogger("uvicorn.error")


router = APIRouter(
    prefix="/api/v1/typical-cases",
    tags=["典型案例重排序"],
)


@router.post(
    "/rerank",
    summary="典型案例融合重排序",
)
def rerank_typical_cases(
    request_data: RerankRequest,
    request: Request,
) -> dict[str, Any]:
    """
    接口3：

    1. 根据retrieval_id读取接口2保存的候选案例；
    2. 读取接口1保存的争议焦点向量与可靠性；
    3. 使用MLP计算争议焦点匹配得分；
    4. 按照以下公式计算最终得分：

       final_score
       = fact_similarity
       + 0.3 * issue_score

    5. 按最终得分降序排列；
    6. 批量读取候选案例正文；
    7. 返回最终结果。
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

    rerank_service = (
        resources.rerank_service
    )

    if rerank_service is None:
        raise HTTPException(
            status_code=503,
            detail="重排序服务尚未初始化",
        )

    try:
        result = rerank_service.rerank(
            retrieval_id=(
                request_data.retrieval_id
            ),
            top_k=request_data.top_k,
        )

        return {
            "code": 0,
            "message": "success",
            "data": result,
        }

    except KeyError as exc:
        # retrieval_id不存在，或者服务重启后已经失效
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
            "典型案例融合重排序失败"
        )

        raise HTTPException(
            status_code=500,
            detail=(
                "典型案例融合重排序失败："
                f"{exc}"
            ),
        ) from exc