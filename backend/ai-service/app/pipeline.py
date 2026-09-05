from __future__ import annotations

from collections import OrderedDict
from typing import Any, Sequence

from .clients.lexpro import ENTITY_LABELS, LexProClient
from .unicode_offsets import (
    CanonicalDocument,
    SourceBlock,
    TextContractError,
    assemble_canonical_document,
    locate_entities,
    verify_located_entity,
)


ENTITY_CODES = {
    "犯罪嫌疑人": "SUSPECT",
    "地名": "LOCATION",
    "组织机构名": "ORGANIZATION",
    "时间": "TIME",
    "罪名": "CRIME",
    "毒品种类": "DRUG",
}


def _mineru_items(payload: dict[str, Any]) -> list[Any]:
    if payload.get("status") != "success":
        raise TextContractError("MINERU_RESPONSE_NOT_SUCCESSFUL")
    data = payload.get("data")
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for key in ("content_list", "blocks", "elements"):
            if isinstance(data.get(key), list):
                return data[key]
    raise TextContractError("MINERU_RESPONSE_HAS_NO_CONTENT_LIST")


def canonical_document_from_mineru(payload: dict[str, Any], *, allow_empty: bool = False) -> CanonicalDocument | None:
    blocks: list[SourceBlock] = []
    for source_index, item in enumerate(_mineru_items(payload)):
        if allow_empty and (not isinstance(item, dict) or not isinstance(item.get("type"), str)
                            or (item.get("type") == "text" and not isinstance(item.get("text"), str))):
            raise TextContractError("MINERU_RESPONSE_INVALID")
        if not isinstance(item, dict) or item.get("type") != "text":
            continue
        text = item.get("text")
        if not isinstance(text, str) or text == "" or (allow_empty and not text.strip()):
            continue
        bbox_value = item.get("bbox")
        bbox = None
        if isinstance(bbox_value, list) and len(bbox_value) == 4 and all(
            isinstance(value, (int, float)) for value in bbox_value
        ):
            bbox = tuple(float(value) for value in bbox_value)
        page_index = item.get("page_idx")
        page_no = page_index + 1 if isinstance(page_index, int) and page_index >= 0 else None
        blocks.append(
            SourceBlock(
                block_id=f"mineru-{source_index:06d}",
                text=text,
                order=len(blocks),
                page_no=page_no,
                bbox=bbox,
                coordinate_unit="MINERU_PAGE_COORDINATE" if bbox else None,
            )
        )
    if allow_empty and not blocks:
        return None
    return assemble_canonical_document(blocks)


def recognize_document(document: CanonicalDocument, client: LexProClient) -> dict[str, Any]:
    model_outputs = client.extract([block.text for block in document.blocks])
    if len(model_outputs) != len(document.blocks):
        raise TextContractError("LEXPRO_BLOCK_COUNT_MISMATCH")

    occurrences = []
    grouped: OrderedDict[tuple[str, str], dict[str, Any]] = OrderedDict()
    for block, candidates in zip(document.blocks, model_outputs, strict=True):
        for category, _ in candidates:
            if category not in ENTITY_LABELS:
                raise TextContractError("UNKNOWN_ENTITY_CATEGORY")
        coded_candidates: Sequence[tuple[str, str]] = tuple(
            (ENTITY_CODES[category], entity_text) for category, entity_text in candidates
        )
        for entity in locate_entities(document, block.block_id, coded_candidates):
            verify_located_entity(document, entity)
            item = {
                "type": entity.category,
                "text": entity.text,
                "blockId": entity.block_id,
                "blockStartUtf16": entity.block_start_utf16,
                "blockEndUtf16": entity.block_end_utf16,
                "globalStartUtf16": entity.global_start_utf16,
                "globalEndUtf16": entity.global_end_utf16,
                "occurrenceIndex": entity.occurrence_index,
                "prefix": entity.prefix,
                "suffix": entity.suffix,
            }
            occurrences.append(item)
            key = (entity.category, entity.text)
            aggregate = grouped.setdefault(
                key,
                {
                    "type": entity.category,
                    "text": entity.text,
                    "occurrenceCount": 0,
                    "blockIds": [],
                },
            )
            aggregate["occurrenceCount"] += 1
            if entity.block_id not in aggregate["blockIds"]:
                aggregate["blockIds"].append(entity.block_id)

    return {
        "schemaVersion": "lexpro.entity.v2",
        "offsetUnit": "UTF16_CODE_UNIT",
        "sourceTextSha256": document.text_sha256,
        "entities": occurrences,
        "documentEntities": list(grouped.values()),
    }
