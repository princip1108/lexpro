from datetime import date
from typing import Any

from pydantic import BaseModel, Field, model_validator


class ModelInfo(BaseModel):
    name: str
    version: str
    dimension: int
    distance: str


class TypicalCaseInput(BaseModel):
    clientRef: str = Field(min_length=1, max_length=100)
    externalCaseId: str = Field(min_length=1, max_length=100)
    title: str = Field(min_length=1, max_length=255)
    caseCause: str | None = Field(default=None, max_length=255)
    caseCauses: list[str] = Field(default_factory=list, max_length=50)
    caseType: str | None = Field(default=None, max_length=50)
    country: str | None = Field(default=None, max_length=100)
    court: str | None = Field(default=None, max_length=255)
    courtLevel: str | None = Field(default=None, max_length=50)
    docType: str | None = Field(default=None, max_length=50)
    disputeFocus: list[str] = Field(default_factory=list, max_length=100)
    judgmentDate: date | None = None
    procedure: str | None = Field(default=None, max_length=100)
    applicableLaws: list[str] = Field(default_factory=list, max_length=100)
    caseLevel: str | None = Field(default=None, max_length=50)
    content: str | None = None
    fact: str | None = None

    @model_validator(mode="after")
    def content_present(self):
        if not clean_text(self.content) and not clean_text(self.fact):
            raise ValueError("content or fact is required")
        return self


class NormalizeRequest(BaseModel):
    schemaVersion: str = "1.0"
    requestId: str = Field(min_length=8, max_length=100)
    cases: list[TypicalCaseInput] = Field(min_length=1, max_length=50)


class NormalizedTypicalCase(TypicalCaseInput):
    embedding: list[float]


class NormalizeResponse(BaseModel):
    schemaVersion: str = "1.0"
    requestId: str
    model: ModelInfo
    cases: list[NormalizedTypicalCase]


class RetrievalFilters(BaseModel):
    caseCause: str | None = Field(default=None, max_length=255)
    caseType: str | None = Field(default=None, max_length=50)
    courtLevel: str | None = Field(default=None, max_length=50)
    judgmentYearFrom: int | None = Field(default=None, ge=1900, le=2200)
    judgmentYearTo: int | None = Field(default=None, ge=1900, le=2200)

    @model_validator(mode="after")
    def valid_year_range(self):
        if (self.judgmentYearFrom is not None and self.judgmentYearTo is not None
                and self.judgmentYearFrom > self.judgmentYearTo):
            raise ValueError("judgmentYearFrom must not exceed judgmentYearTo")
        return self


class RetrieveRequest(BaseModel):
    schemaVersion: str = "1.0"
    requestId: str = Field(min_length=8, max_length=100)
    factText: str = Field(min_length=1, max_length=100000)
    disputeFocus: list[str] = Field(default_factory=list, max_length=100)
    filters: RetrievalFilters = Field(default_factory=RetrievalFilters)
    limit: int = Field(default=10, ge=1, le=50)


class RetrievalItem(BaseModel):
    typicalCaseId: int
    rank: int
    score: float = Field(ge=0, le=1)
    lexicalScore: float = Field(ge=0, le=1)
    vectorScore: float | None = Field(default=None, ge=-1, le=1)
    reasons: list[dict[str, Any]]


class RetrieveResponse(BaseModel):
    schemaVersion: str = "1.0"
    requestId: str
    model: ModelInfo
    pipelineVersion: str = "m7-hybrid-v1"
    index: dict[str, Any]
    degraded: bool
    degradationReason: str | None = None
    queryEmbedding: list[float] | None = None
    items: list[RetrievalItem]


def clean_text(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = " ".join(value.split())
    return normalized or None
