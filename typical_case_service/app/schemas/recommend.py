from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
)

from app.schemas.retrieve import RetrievalFilters


class TypicalCaseSearchRequest(BaseModel):
    """
    典型案例最终检索请求。

    前端先通过/analyze展示案件分析结果，
    再将analysis_id和筛选条件提交到本接口。

    后端自动完成：
    数据库召回 → MLP重排序 → 正文读取。
    """

    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
    )

    analysis_id: str | None = Field(
        default=None,
        min_length=1,
        description=(
            "接口1返回的案件分析任务ID。"
            "如果不传，则默认使用最近一次/analyze结果。"
        ),
    )

    filters: RetrievalFilters = Field(
        default_factory=RetrievalFilters,
        description="案例结构化筛选条件",
    )

    top_k: int = Field(
        default=100,
        ge=1,
        le=100,
        description="最终返回的典型案例数量",
    )