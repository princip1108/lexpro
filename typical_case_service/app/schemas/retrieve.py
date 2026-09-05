from __future__ import annotations

from datetime import date
from typing import Literal

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
)


CaseLevel = Literal[
    "普通案例",
    "典型案例",
    "参考性案例",
    "指导性案例",
]


CourtLevel = Literal[
    "基层法院",
    "中级法院",
    "高级法院",
    "最高法",
    "最高检",
    "人民法院案例库",
]


CaseType = Literal[
    "刑事",
    "民事",
    "行政",
    "公益诉讼",
    "执行",
    "赔偿"
]


class RetrievalFilters(BaseModel):
    """
    案例结构化筛选条件。

    所有字段均为可选。
    前端未传递的字段，不参与数据库过滤。
    """

    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
    )

    # 1. 标题
    title: str | None = Field(
        default=None,
        description="案例标题关键词，后端进行模糊匹配",
        examples=["信用卡诈骗"],
    )

    # 2. 罪名
    casecauses: list[str] | None = Field(
        default=None,
        description=(
            "罪名列表，后端同时匹配"
            "casecause和casecausefull字段"
        ),
        examples=[
            ["盗窃罪", "信用卡诈骗罪"]
        ],
    )

    # 3. 法条
    applicable_laws: list[str] | None = Field(
        default=None,
        description="适用法条列表",
        examples=[
            [
                "中华人民共和国刑法第二百六十四条",
                "中华人民共和国刑法第一百九十六条",
            ]
        ],
    )

    # 4. 案件等级
    caselevel: CaseLevel | None = Field(
        default=None,
        description="案件等级",
        examples=["典型案例"],
    )

    # 5. 法院等级
    courtlevel: CourtLevel | None = Field(
        default=None,
        description="法院等级",
        examples=["基层法院"],
    )

    # 6. 地区
    county: str | None = Field(
        default=None,
        description="地区关键词",
        examples=["北京市"],
    )

    # 7. 判决时间
    judgedate: date | None = Field(
        default=None,
        description=(
            "判决日期截止时间，检索该日期及之前的案件"
        ),
        examples=["2024-12-31"],
    )

    # 8. 审理程序
    procedure: str | None = Field(
        default=None,
        description="审理程序",
        examples=["一审"],
    )

    # 9. 文书类型
    doctype: str | None = Field(
        default=None,
        description="文书类型",
        examples=["判决书"],
    )

    # 11. 法院名称
    court: str | None = Field(
        default=None,
        description="法院名称关键词，后端进行模糊匹配",
        examples=["北京市朝阳区人民法院"],
    )

    # 12. 案件类型
    casetype: CaseType | None = Field(
        default=None,
        description="案件类型",
        examples=["刑事"],
    )

    @field_validator(
        "casecauses",
        "applicable_laws",
        mode="after",
    )
    @classmethod
    def clean_string_list(
        cls,
        values: list[str] | None,
    ) -> list[str] | None:
        """
        清理列表中的空字符串，并去除重复项。
        """
        if values is None:
            return None

        cleaned_values: list[str] = []

        for value in values:
            cleaned_value = value.strip()

            if (
                cleaned_value
                and cleaned_value not in cleaned_values
            ):
                cleaned_values.append(cleaned_value)

        return cleaned_values or None


class TypicalRetrieveRequest(BaseModel):
    """
    典型案例检索请求。

    查询范围固定为：
    core_typical、core_2025、core_2024、core_2023。

    案件事实已经由接口1处理，
    此接口根据analysis_id读取查询向量。
    """

    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
    )

    analysis_id: str = Field(
        ...,
        min_length=1,
        description="接口1返回的案件分析任务ID",
    )

    filters: RetrievalFilters = Field(
        default_factory=RetrievalFilters,
        description="结构化筛选条件",
    )

    top_k: int = Field(
        default=100,
        ge=1,
        le=100,
        description="最终保留的候选案例数量",
    )


class OrdinaryCaseSearchRequest(BaseModel):
    """
    普通案例检索请求。

    普通案例检索流程：
    案件事实DELTA编码
    → 根据前端选择年份查询对应分表
    → 返回数据库检索结果。

    不调用Qwen，不提取争议焦点，也不调用MLP。
    """

    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
    )

    # 10. 案件事实
    case_fact: str = Field(
        ...,
        min_length=5,
        description="用于向量检索的案件事实",
        examples=[
            (
                "行为人取得他人银行卡后，"
                "在未经持卡人允许的情况下，"
                "多次使用该银行卡提取现金。"
            )
        ],
    )

    years: list[int] = Field(
        ...,
        min_length=1,
        description="需要检索的案件年份",
        examples=[
            [2023, 2024, 2025]
        ],
    )

    filters: RetrievalFilters = Field(
        default_factory=RetrievalFilters,
        description="结构化筛选条件",
    )

    top_k: int = Field(
        default=100,
        ge=1,
        le=100,
        description="最终返回的案例数量",
    )

    @field_validator(
        "years",
        mode="after",
    )
    @classmethod
    def validate_years(
        cls,
        years: list[int],
    ) -> list[int]:
        """
        校验年份，并按照从新到旧排列。
        """
        unique_years = sorted(
            set(years),
            reverse=True,
        )

        invalid_years = [
            year
            for year in unique_years
            if year < 2000 or year > 2025
        ]

        if invalid_years:
            raise ValueError(
                f"不支持的年份：{invalid_years}"
            )

        return unique_years