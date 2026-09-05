from fastapi import APIRouter, HTTPException, Request

from app.schemas.analyze import AnalyzeRequest


router = APIRouter(
    prefix="/api/v1/typical-cases",
    tags=["典型案例推送"],
)


@router.post("/analyze")
def analyze_case(
    payload: AnalyzeRequest,
    request: Request,
):
    """
    接收案件事实，返回争议焦点及其事实覆盖度。

    案件整体embedding、争议焦点embedding等中间结果
    通过analysis_id保存在后端，不返回给前端。
    """
    resources = request.app.state.resources

    if resources.analyze_service is None:
        raise HTTPException(
            status_code=503,
            detail="案件分析服务尚未初始化",
        )

    try:
        result = resources.analyze_service.analyze(
            case_fact=payload.case_fact,
        )

    except ValueError as exc:
        raise HTTPException(
            status_code=400,
            detail=str(exc),
        ) from exc

    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"案件分析失败：{exc}",
        ) from exc

    return {
        "code": 0,
        "message": "success",
        "data": result,
    }