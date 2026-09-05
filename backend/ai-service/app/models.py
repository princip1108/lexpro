from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


def to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class RecognitionBlock(ApiModel):
    block_id: str = Field(min_length=1, max_length=100)
    text: str = Field(min_length=1)
    order: int = Field(ge=0)
    page_no: int | None = Field(default=None, ge=1)
    bbox: tuple[float, float, float, float] | None = None
    coordinate_unit: str | None = Field(default=None, max_length=50)


class RecognitionRequest(ApiModel):
    request_id: str = Field(min_length=1, max_length=100)
    source_text_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    blocks: list[RecognitionBlock] = Field(min_length=1, max_length=5000)

