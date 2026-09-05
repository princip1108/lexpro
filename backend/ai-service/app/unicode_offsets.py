from __future__ import annotations

from dataclasses import dataclass
from hashlib import sha256
from typing import Iterable, Sequence


BLOCK_SEPARATOR = "\n\n"


class TextContractError(ValueError):
    """Raised when source text cannot satisfy the positioning contract."""


@dataclass(frozen=True)
class SourceBlock:
    block_id: str
    text: str
    order: int
    page_no: int | None = None
    bbox: tuple[float, float, float, float] | None = None
    coordinate_unit: str | None = None


@dataclass(frozen=True)
class CanonicalBlock:
    block_id: str
    text: str
    order: int
    page_no: int | None
    bbox: tuple[float, float, float, float] | None
    coordinate_unit: str | None
    block_text_sha256: str
    global_start_utf16: int
    global_end_utf16: int


@dataclass(frozen=True)
class CanonicalDocument:
    text: str
    text_sha256: str
    blocks: tuple[CanonicalBlock, ...]


@dataclass(frozen=True)
class LocatedEntity:
    category: str
    text: str
    block_id: str
    block_start_utf16: int
    block_end_utf16: int
    global_start_utf16: int
    global_end_utf16: int
    occurrence_index: int
    prefix: str
    suffix: str


def validate_unicode_text(text: str) -> None:
    if "\x00" in text:
        raise TextContractError("SOURCE_TEXT_CONTAINS_NUL")
    if "\ufffd" in text:
        raise TextContractError("SOURCE_TEXT_CONTAINS_REPLACEMENT_CHARACTER")
    for character in text:
        value = ord(character)
        if 0xD800 <= value <= 0xDFFF:
            raise TextContractError("SOURCE_TEXT_CONTAINS_UNPAIRED_SURROGATE")


def canonicalize_text(text: str, *, strip_initial_bom: bool = False) -> str:
    validate_unicode_text(text)
    if strip_initial_bom and text.startswith("\ufeff"):
        text = text[1:]
    return text.replace("\r\n", "\n").replace("\r", "\n").replace("\u2028", "\n").replace("\u2029", "\n")


def text_sha256(text: str) -> str:
    return sha256(text.encode("utf-8")).hexdigest()


def utf16_length(text: str) -> int:
    validate_unicode_text(text)
    return len(text.encode("utf-16-le")) // 2


def codepoint_to_utf16_map(text: str) -> tuple[int, ...]:
    validate_unicode_text(text)
    offsets = [0]
    current = 0
    for character in text:
        current += 2 if ord(character) > 0xFFFF else 1
        offsets.append(current)
    return tuple(offsets)


def codepoint_span_to_utf16(text: str, start: int, end: int) -> tuple[int, int]:
    if not 0 <= start < end <= len(text):
        raise TextContractError("INVALID_CODEPOINT_SPAN")
    mapping = codepoint_to_utf16_map(text)
    return mapping[start], mapping[end]


def slice_utf16(text: str, start: int, end: int) -> str:
    if not 0 <= start <= end <= utf16_length(text):
        raise TextContractError("INVALID_UTF16_SPAN")
    encoded = text.encode("utf-16-le")
    try:
        return encoded[start * 2:end * 2].decode("utf-16-le")
    except UnicodeDecodeError as exception:
        raise TextContractError("UTF16_SPAN_SPLITS_SURROGATE_PAIR") from exception


def assemble_canonical_document(blocks: Iterable[SourceBlock]) -> CanonicalDocument:
    source_blocks = tuple(blocks)
    if not source_blocks:
        raise TextContractError("DOCUMENT_HAS_NO_TEXT_BLOCKS")
    if len({block.block_id for block in source_blocks}) != len(source_blocks):
        raise TextContractError("DUPLICATE_BLOCK_ID")
    if any(not block.block_id.strip() for block in source_blocks):
        raise TextContractError("BLANK_BLOCK_ID")
    if any(block.order < 0 for block in source_blocks):
        raise TextContractError("INVALID_BLOCK_ORDER")
    if len({block.order for block in source_blocks}) != len(source_blocks):
        raise TextContractError("DUPLICATE_BLOCK_ORDER")

    ordered = sorted(source_blocks, key=lambda block: block.order)
    canonical_parts: list[str] = []
    canonical_blocks: list[CanonicalBlock] = []
    global_offset = 0
    for index, block in enumerate(ordered):
        canonical = canonicalize_text(block.text, strip_initial_bom=index == 0)
        if canonical == "":
            continue
        if canonical_parts:
            canonical_parts.append(BLOCK_SEPARATOR)
            global_offset += utf16_length(BLOCK_SEPARATOR)
        start = global_offset
        end = start + utf16_length(canonical)
        canonical_parts.append(canonical)
        canonical_blocks.append(
            CanonicalBlock(
                block_id=block.block_id,
                text=canonical,
                order=block.order,
                page_no=block.page_no,
                bbox=block.bbox,
                coordinate_unit=block.coordinate_unit,
                block_text_sha256=text_sha256(canonical),
                global_start_utf16=start,
                global_end_utf16=end,
            )
        )
        global_offset = end

    if not canonical_blocks:
        raise TextContractError("DOCUMENT_HAS_NO_TEXT_BLOCKS")
    document_text = "".join(canonical_parts)
    return CanonicalDocument(
        text=document_text,
        text_sha256=text_sha256(document_text),
        blocks=tuple(canonical_blocks),
    )


def locate_entities(
    document: CanonicalDocument,
    block_id: str,
    candidates: Sequence[tuple[str, str]],
    *,
    context_codepoints: int = 16,
) -> tuple[LocatedEntity, ...]:
    """Locate ordered model entities as exact, repeated occurrences in one block.

    Model responses contain category/text but no trustworthy offsets. Repeated
    identical outputs are matched to repeated source occurrences from left to
    right. A candidate that cannot be matched exactly fails the whole block so
    callers never persist a partially fabricated positioning result.
    """

    block = next((item for item in document.blocks if item.block_id == block_id), None)
    if block is None:
        raise TextContractError("UNKNOWN_BLOCK_ID")
    if context_codepoints < 0:
        raise TextContractError("INVALID_CONTEXT_LENGTH")

    next_search_start: dict[tuple[str, str], int] = {}
    occurrence_counts: dict[tuple[str, str], int] = {}
    mapping = codepoint_to_utf16_map(block.text)
    located: list[LocatedEntity] = []
    for category, entity_text in candidates:
        if not category.strip():
            raise TextContractError("BLANK_ENTITY_CATEGORY")
        if not entity_text.strip():
            raise TextContractError("BLANK_ENTITY_TEXT")
        validate_unicode_text(entity_text)
        entity_key = (category, entity_text)
        search_start = next_search_start.get(entity_key, 0)
        start_codepoint = block.text.find(entity_text, search_start)
        if start_codepoint < 0:
            raise TextContractError("ENTITY_TEXT_NOT_FOUND_IN_BLOCK")
        end_codepoint = start_codepoint + len(entity_text)
        start_utf16 = mapping[start_codepoint]
        end_utf16 = mapping[end_codepoint]
        if slice_utf16(block.text, start_utf16, end_utf16) != entity_text:
            raise TextContractError("ENTITY_UTF16_ROUND_TRIP_FAILED")

        occurrence_index = occurrence_counts.get(entity_key, 0)
        occurrence_counts[entity_key] = occurrence_index + 1
        next_search_start[entity_key] = end_codepoint
        located.append(
            LocatedEntity(
                category=category,
                text=entity_text,
                block_id=block.block_id,
                block_start_utf16=start_utf16,
                block_end_utf16=end_utf16,
                global_start_utf16=block.global_start_utf16 + start_utf16,
                global_end_utf16=block.global_start_utf16 + end_utf16,
                occurrence_index=occurrence_index,
                prefix=block.text[max(0, start_codepoint - context_codepoints):start_codepoint],
                suffix=block.text[end_codepoint:end_codepoint + context_codepoints],
            )
        )
    return tuple(located)


def verify_located_entity(document: CanonicalDocument, entity: LocatedEntity) -> None:
    block = next((item for item in document.blocks if item.block_id == entity.block_id), None)
    if block is None:
        raise TextContractError("UNKNOWN_BLOCK_ID")
    if slice_utf16(block.text, entity.block_start_utf16, entity.block_end_utf16) != entity.text:
        raise TextContractError("ENTITY_BLOCK_TEXT_MISMATCH")
    if slice_utf16(document.text, entity.global_start_utf16, entity.global_end_utf16) != entity.text:
        raise TextContractError("ENTITY_DOCUMENT_TEXT_MISMATCH")
