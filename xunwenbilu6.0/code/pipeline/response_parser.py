from __future__ import annotations

import re
from collections import OrderedDict
from typing import Any


ENTITY_LABELS = ["犯罪嫌疑人", "地名", "组织机构名", "时间", "罪名", "毒品种类"]


def parse_llm_response(response: str) -> list[dict[str, str]]:
    content = response or ""
    if "<Labeled End>" in content:
        content = content.split("<Labeled End>", 1)[0]
    if re.search(r"(?:####\s*)?None", content, re.IGNORECASE):
        return []
    matches = re.findall(
        r"category\s*[：:]\s*([^\n#]+)\s*\n+\s*#{0,4}\s*entity\s*[：:]\s*([^\n#]+)",
        content,
        flags=re.IGNORECASE,
    )
    entities: list[dict[str, str]] = []
    for category, entity in matches:
        category, entity = category.strip(), entity.strip()
        if category in ENTITY_LABELS and entity:
            entities.append({"category": category, "entity": entity})
    for category, entity in re.findall(
        r"category\s*[：:]\s*(.+?)\s+entity\s*[：:]\s*([^\n#]+)",
        content,
        flags=re.IGNORECASE,
    ):
        category = re.sub(r"[，,。.、\s#]+$", "", category.strip())
        entity = re.sub(r"[，,。.、\s#]+$", "", entity.strip())
        item = {"category": category, "entity": entity}
        if category in ENTITY_LABELS and entity and item not in entities:
            entities.append(item)
    return entities


def align_entities(text: str, entities: list[dict[str, str]]) -> list[dict[str, Any]]:
    aligned: list[dict[str, Any]] = []
    for item in entities:
        entity = item["entity"]
        start = text.find(entity)
        if start >= 0:
            aligned.append({
                "category": item["category"],
                "entity": entity,
                "start": start,
                "end": start + len(entity),
            })
    return aligned


def deduplicate_document_entities(sentences: list[dict[str, Any]]) -> list[dict[str, Any]]:
    grouped: OrderedDict[tuple[str, str], dict[str, Any]] = OrderedDict()
    for sentence in sentences:
        sentence_id = int(sentence["sentence_id"])
        for entity in sentence.get("entities", []):
            key = (entity["category"], entity["entity"])
            if key not in grouped:
                grouped[key] = {
                    "category": key[0],
                    "entity": key[1],
                    "occurrence_count": 0,
                    "sentence_ids": [],
                }
            grouped[key]["occurrence_count"] += 1
            if sentence_id not in grouped[key]["sentence_ids"]:
                grouped[key]["sentence_ids"].append(sentence_id)
    return list(grouped.values())
