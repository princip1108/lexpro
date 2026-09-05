from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    """
    案件分析接口请求参数。
    """

    case_fact: str = Field(
        ...,
        min_length=5,
        description="待分析案件的案件事实",
        examples=[
            (
                "被告人张某因琐事与被害人李某发生争执，"
                "后持刀刺伤李某腹部。经鉴定，李某的损伤"
                "程度为重伤二级。张某案发后主动到公安机关"
                "投案，并如实供述了主要犯罪事实。"
            )
        ],
    )