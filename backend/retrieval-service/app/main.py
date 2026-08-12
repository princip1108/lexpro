import logging

from fastapi import Depends, FastAPI, HTTPException

from .config import Settings, get_settings
from .embedding import EmbeddingModel
from .models import (
    ModelInfo,
    NormalizeRequest,
    NormalizeResponse,
    NormalizedTypicalCase,
    RetrieveRequest,
    RetrieveResponse,
    clean_text,
)
from .ranking import fuse_and_rerank, lexical_score
from .repository import TypicalCaseRepository

LOGGER = logging.getLogger(__name__)
app = FastAPI(title="LexPro Retrieval Service", version="1.0.0", docs_url=None, redoc_url=None)
_models: dict[tuple[str, str], EmbeddingModel] = {}


def embedding_model(settings: Settings) -> EmbeddingModel:
    key = (settings.model_name, settings.model_revision)
    if key not in _models:
        _models[key] = EmbeddingModel(settings)
    return _models[key]


def model_info(settings: Settings) -> ModelInfo:
    return ModelInfo(
        name=settings.model_name,
        version=settings.model_revision,
        dimension=settings.dimension,
        distance=settings.distance,
    )


@app.get("/internal/v1/health")
def health(settings: Settings = Depends(get_settings)):
    return {"status": "UP", "model": model_info(settings).model_dump()}


@app.post("/internal/v1/normalize", response_model=NormalizeResponse)
def normalize(request: NormalizeRequest, settings: Settings = Depends(get_settings)):
    texts = ["\n".join(filter(None, [item.title, item.caseCause, item.fact, item.content])) for item in request.cases]
    try:
        vectors = embedding_model(settings).encode(texts)
    except Exception as exception:
        LOGGER.exception("Typical-case embedding failed requestId=%s", request.requestId)
        raise HTTPException(status_code=503, detail="EMBEDDING_MODEL_UNAVAILABLE") from exception

    normalized = []
    for item, vector in zip(request.cases, vectors, strict=True):
        data = item.model_dump()
        for field in ("title", "caseCause", "country", "court", "procedure", "content", "fact"):
            data[field] = clean_text(data[field])
        for field in ("caseCauses", "disputeFocus", "applicableLaws"):
            data[field] = list(dict.fromkeys(filter(None, (clean_text(value) for value in data[field]))))
        data["embedding"] = vector
        normalized.append(NormalizedTypicalCase(**data))
    return NormalizeResponse(requestId=request.requestId, model=model_info(settings), cases=normalized)


@app.post("/internal/v1/retrieve", response_model=RetrieveResponse)
def retrieve(request: RetrieveRequest, settings: Settings = Depends(get_settings)):
    repository = TypicalCaseRepository(settings.database_url, settings.max_candidates)
    try:
        candidates = repository.candidates(request.filters)
    except Exception as exception:
        LOGGER.exception("Typical-case corpus query failed requestId=%s", request.requestId)
        raise HTTPException(status_code=503, detail="RETRIEVAL_DATABASE_UNAVAILABLE") from exception

    lexical = sorted(
        ((case_id, lexical_score(request.factText, candidate)) for case_id, candidate in candidates.items()),
        key=lambda item: (-item[1], item[0]),
    )[: max(request.limit * 5, 50)]

    degraded = False
    degradation_reason = None
    query_embedding = None
    vector: list[tuple[int, float]] = []
    try:
        query_embedding = embedding_model(settings).encode([request.factText])[0]
        vector = repository.vector_candidates(query_embedding, request.filters, max(request.limit * 5, 50))
        missing_ids = [case_id for case_id, _ in vector if case_id not in candidates]
        candidates.update(repository.candidates_by_ids(missing_ids))
    except Exception:
        degraded = True
        degradation_reason = "VECTOR_RECALL_UNAVAILABLE"
        LOGGER.warning("Vector recall degraded to lexical requestId=%s", request.requestId, exc_info=True)

    items = fuse_and_rerank(
        request.factText, request.disputeFocus, candidates, lexical, vector, request.limit
    )
    return RetrieveResponse(
        requestId=request.requestId,
        model=model_info(settings),
        index={"type": "HNSW", "operatorClass": "vector_cosine_ops", "fusion": "RRF"},
        degraded=degraded,
        degradationReason=degradation_reason,
        queryEmbedding=query_embedding,
        items=items,
    )
