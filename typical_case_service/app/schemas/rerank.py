from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
)


class RerankRequest(BaseModel):
    """
    典型案例重排序请求。

    retrieval_id由接口2返回，
    后端根据该ID读取争议焦点向量、
    候选案例向量和事实相似度。
    """

    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
    )

    retrieval_id: str = Field(
        ...,
        min_length=1,
        description="接口2返回的检索任务ID",
        examples=[
            "adebc642-022a-4334-98a9-8d5726fd26e4"
        ],
    )

    top_k: int = Field(
        default=100,
        ge=1,
        le=100,
        description="融合重排序后返回的案例数量",
        examples=[100],
    )