from __future__ import annotations

import hmac
import logging
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool

from .clients.lexpro import LexProClient, LexProClientError
from .clients.mineru import MinerUClient, MinerUClientError
from .config import Settings, get_settings
from .models import RecognitionRequest
from .pipeline import canonical_document_from_mineru, recognize_document
from .unicode_offsets import SourceBlock, TextContractError, assemble_canonical_document
from .word import WordDocumentError, parse_docx


LOGGER = logging.getLogger(__name__)
ALLOWED_MEDIA_TYPES = {
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "image/jpeg",
    "image/png",
}
ALLOWED_EXTENSIONS = {".pdf", ".doc", ".docx", ".jpg", ".jpeg", ".png"}

app = FastAPI(title="LexPro AI Adapter Service", version="0.1.0", docs_url=None, redoc_url=None)


def require_internal_token(
    token: Annotated[str | None, Header(alias="X-LexPro-Internal-Token")] = None,
    settings: Settings = Depends(get_settings),
) -> None:
    expected = settings.internal_token.get_secret_value()
    if token is None or not hmac.compare_digest(token, expected):
        raise HTTPException(status_code=401, detail="INVALID_INTERNAL_TOKEN")


def mineru_client(settings: Settings) -> MinerUClient:
    return MinerUClient(
        str(settings.mineru_base_url),
        connect_timeout_seconds=settings.connect_timeout_seconds,
        read_timeout_seconds=settings.mineru_read_timeout_seconds,
        max_file_bytes=settings.max_file_bytes,
    )


def lexpro_client(settings: Settings) -> LexProClient:
    return LexProClient(
        str(settings.lexpro_base_url),
        model_name=settings.lexpro_model_name,
        batch_size=settings.batch_size,
        connect_timeout_seconds=settings.connect_timeout_seconds,
        read_timeout_seconds=settings.lexpro_read_timeout_seconds,
    )


@app.exception_handler(TextContractError)
async def text_contract_error_handler(_request: Request, exception: TextContractError):
    return JSONResponse(status_code=422, content={"code": str(exception), "message": "文本或实体位置校验失败"})


@app.get("/internal/v1/health", dependencies=[Depends(require_internal_token)])
def health(settings: Settings = Depends(get_settings)):
    mineru_up = mineru_client(settings).health()
    lexpro_up = lexpro_client(settings).health()
    return {
        "status": "UP" if mineru_up and lexpro_up else "DEGRADED",
        "components": {
            "mineru": {"status": "UP" if mineru_up else "DOWN"},
            "lexpro": {"status": "UP" if lexpro_up else "DOWN", "model": settings.lexpro_model_name},
        },
    }


@app.get("/internal/v1/readiness", dependencies=[Depends(require_internal_token)])
def readiness(settings: Settings = Depends(get_settings)):
    mineru_up = mineru_client(settings).health()
    lexpro_up = lexpro_client(settings).health()
    if not mineru_up or not lexpro_up:
        raise HTTPException(status_code=503, detail="MODEL_SERVICES_NOT_READY")
    return {"status": "READY"}


@app.post("/internal/v1/documents/parse", dependencies=[Depends(require_internal_token)])
async def parse_document(
    request: Request,
    file_name: Annotated[str, Query(alias="fileName", min_length=1, max_length=255)],
    settings: Settings = Depends(get_settings),
):
    media_type = request.headers.get("content-type", "").split(";", 1)[0].strip().lower()
    extension = Path(file_name).suffix.lower()
    if media_type not in ALLOWED_MEDIA_TYPES or extension not in ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=415, detail="UNSUPPORTED_DOCUMENT_TYPE")
    declared_length = request.headers.get("content-length")
    if declared_length and declared_length.isdigit() and int(declared_length) > settings.max_file_bytes:
        raise HTTPException(status_code=413, detail="DOCUMENT_TOO_LARGE")
    content = await request.body()
    if len(content) > settings.max_file_bytes:
        raise HTTPException(status_code=413, detail="DOCUMENT_TOO_LARGE")
    request_id = request.headers.get("x-request-id", "unknown")[:100]
    parser = "mineru"
    parser_version = settings.mineru_version
    warnings = []
    try:
        if extension == ".doc":
            raise WordDocumentError("LEGACY_WORD_CONVERSION_REQUIRED")
        if extension == ".docx":
            document = await run_in_threadpool(parse_docx, content, mineru_client(settings), warnings=warnings)
            parser = "docx"
            parser_version = "docx-ooxml/2+" + settings.mineru_version
        else:
            payload = await run_in_threadpool(mineru_client(settings).parse_bytes, file_name, content, media_type)
            document = canonical_document_from_mineru(payload)
    except WordDocumentError as exception:
        raise HTTPException(status_code=422, detail=str(exception)) from exception
    except MinerUClientError as exception:
        LOGGER.warning("mineru_call_failed requestId=%s code=%s", request_id, exception)
        raise HTTPException(status_code=503, detail=str(exception)) from exception
    LOGGER.info(
        "mineru_parse_succeeded requestId=%s bytes=%d blocks=%d utf16Length=%d",
        request_id,
        len(content),
        len(document.blocks),
        len(document.text.encode("utf-16-le")) // 2,
    )
    return {
        "schemaVersion": "lexpro.parse.v2",
        "offsetUnit": "UTF16_CODE_UNIT",
        "parser": parser,
        "parserVersion": parser_version,
        "warnings": warnings,
        "text": document.text,
        "textSha256": document.text_sha256,
        "blocks": [
            {
                "blockId": block.block_id,
                "text": block.text,
                "order": block.order,
                "pageNo": block.page_no,
                "bbox": block.bbox,
                "coordinateUnit": block.coordinate_unit,
                "blockTextSha256": block.block_text_sha256,
                "globalStartUtf16": block.global_start_utf16,
                "globalEndUtf16": block.global_end_utf16,
            }
            for block in document.blocks
        ],
    }


@app.post("/internal/v1/entities/recognize", dependencies=[Depends(require_internal_token)])
def recognize_entities(request: RecognitionRequest, settings: Settings = Depends(get_settings)):
    document = assemble_canonical_document(
        SourceBlock(
            block_id=block.block_id,
            text=block.text,
            order=block.order,
            page_no=block.page_no,
            bbox=block.bbox,
            coordinate_unit=block.coordinate_unit,
        )
        for block in request.blocks
    )
    if document.text_sha256 != request.source_text_sha256:
        raise HTTPException(status_code=409, detail="SOURCE_TEXT_VERSION_MISMATCH")
    try:
        result = recognize_document(document, lexpro_client(settings))
    except LexProClientError as exception:
        LOGGER.warning("lexpro_call_failed requestId=%s code=%s", request.request_id, exception)
        raise HTTPException(status_code=503, detail=str(exception)) from exception
    LOGGER.info(
        "lexpro_recognition_succeeded requestId=%s blocks=%d entities=%d",
        request.request_id,
        len(document.blocks),
        len(result["entities"]),
    )
    return {
        **result,
        "modelName": settings.lexpro_model_name,
        "modelVersion": settings.lexpro_model_version,
    }
