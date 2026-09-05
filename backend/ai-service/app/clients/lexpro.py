from __future__ import annotations

import re
from typing import Any, Sequence

import requests


ENTITY_LABELS = (
    "犯罪嫌疑人",
    "地名",
    "组织机构名",
    "时间",
    "罪名",
    "毒品种类",
)

# Equivalent wire labels for the same approved six classes, not new categories.
ENTITY_LABEL_ALIASES = dict(zip(
    ("suspect", "location", "organization", "time", "crime", "drug"), ENTITY_LABELS
))

ENTITY_DEFINITIONS = {
    "犯罪嫌疑人": "涉嫌实施违法犯罪行为的自然人或法人。",
    "地名": "行政区域或文本中明确作为地点使用的名称。",
    "组织机构名": "机关、单位、企业或其他正式组织名称。",
    "时间": "日期、时刻或明确时间段。",
    "罪名": "刑事法律中的罪名。",
    "毒品种类": "文本明确提及的毒品名称或种类。",
}


class LexProClientError(RuntimeError):
    pass


def create_prompt(text: str) -> str:
    definitions = "\n".join(f"- {label}: {ENTITY_DEFINITIONS[label]}" for label in ENTITY_LABELS)
    return (
        "请你作为法律领域的专家，从询问笔录文本中提取指定类别的实体。\n"
        f"实体类别定义：\n{definitions}\n"
        "只允许返回以下类别：" + "、".join(ENTITY_LABELS) + "。\n"
        "实体文本必须逐字来自输入，不得改写。\n"
        "每个结果使用两行格式：\n"
        "#### category：实体类别\n"
        "#### entity：实体原文\n"
        "没有实体时返回 #### None，最后返回 <Labeled End>。\n"
        f"## 文本内容:\n{text}\n## 提取结果:\n"
    )


def parse_completion(text: str) -> tuple[tuple[str, str], ...]:
    content = text or ""
    if "<Labeled End>" in content:
        content = content.split("<Labeled End>", 1)[0]
    # Some checkpoints emit one empty marker per entity class. Only an explicit,
    # non-empty sequence of these markers denotes an empty extraction.
    lines = [line.strip() for line in content.splitlines() if line.strip()]
    if lines and all(re.fullmatch(r"(?:####\s*)?None", line, re.IGNORECASE) for line in lines):
        return ()
    matches = re.findall(
        r"category\s*[：:]\s*([^\n#]+)\s*\n+\s*#{0,4}\s*entity\s*[：:]\s*([^\n#]+)",
        content,
        flags=re.IGNORECASE,
    )
    # Deployed LexPro checkpoints also emit '#### 类别：实体' lines.
    matches.extend(re.findall(
        r"^####\s*([^\n：:]+)[：:]\s*([^\n]+)$", content,
        flags=re.MULTILINE,
    ))
    entities: list[tuple[str, str]] = []
    for category, entity in matches:
        label = category.strip()
        item = (ENTITY_LABEL_ALIASES.get(label.lower(), label), entity.strip())
        if item[0].lower() in {"category", "entity"}:
            continue
        # Deployed checkpoints sometimes add role-specific labels such as
        # "被询问人" even though the prompt restricts output to six classes.
        # They are not part of the approved contract, so ignore them rather
        # than failing every other valid block in the document.
        if item[0] not in ENTITY_LABELS:
            continue
        if item[1] and item not in entities:
            entities.append(item)
    if matches:
        return tuple(entities)
    # On empty blocks LexPro can repeat "#### None" until the output limit and
    # finish with an incomplete hash prefix. Treat only that narrow shape as
    # an empty extraction; arbitrary prose remains an invalid response.
    empty_remainder = re.sub(r"(?:#{0,4}\s*)?None", "", content, flags=re.IGNORECASE)
    if lines and not re.sub(r"[\s#`]+", "", empty_remainder):
        return ()
    if not entities:
        raise LexProClientError("LEXPRO_RESPONSE_FORMAT_INVALID")
    return tuple(entities)


class LexProClient:
    def __init__(
        self,
        base_url: str,
        *,
        model_name: str = "LexPro_8B",
        batch_size: int = 128,
        connect_timeout_seconds: float = 5.0,
        read_timeout_seconds: float = 900.0,
        session: Any | None = None,
    ) -> None:
        if batch_size < 1:
            raise ValueError("batch_size must be positive")
        self.base_url = base_url.rstrip("/")
        self.model_name = model_name
        self.batch_size = batch_size
        self.connect_timeout_seconds = connect_timeout_seconds
        self.read_timeout_seconds = read_timeout_seconds
        self.session = session or requests.Session()

    def extract(self, texts: Sequence[str]) -> tuple[tuple[tuple[str, str], ...], ...]:
        results: list[tuple[tuple[str, str], ...]] = []
        for offset in range(0, len(texts), self.batch_size):
            batch = texts[offset:offset + self.batch_size]
            payload = {
                "model": self.model_name,
                "prompt": [create_prompt(text) for text in batch],
                "temperature": 0.15,
                "top_p": 0.9,
                "max_tokens": 1024,
                "seed": 42,
                "stop": ["<Labeled End>", "</s>"],
            }
            try:
                response = self.session.post(
                    f"{self.base_url}/v1/completions",
                    json=payload,
                    timeout=(self.connect_timeout_seconds, self.read_timeout_seconds),
                )
                response.raise_for_status()
                response_payload = response.json()
            except (requests.RequestException, ValueError) as exception:
                raise LexProClientError("LEXPRO_SERVICE_UNAVAILABLE") from exception
            choices = response_payload.get("choices") if isinstance(response_payload, dict) else None
            if not isinstance(choices, list):
                raise LexProClientError("LEXPRO_RESPONSE_INVALID")
            ordered = sorted(choices, key=lambda item: item.get("index", -1))
            if len(ordered) != len(batch):
                raise LexProClientError("LEXPRO_RESPONSE_COUNT_MISMATCH")
            if [item.get("index") for item in ordered] != list(range(len(batch))):
                raise LexProClientError("LEXPRO_RESPONSE_INDEX_INVALID")
            results.extend(parse_completion(str(item.get("text", ""))) for item in ordered)
        return tuple(results)

    def health(self) -> bool:
        try:
            response = self.session.get(
                f"{self.base_url}/v1/models",
                timeout=(self.connect_timeout_seconds, min(self.read_timeout_seconds, 5.0)),
            )
            response.raise_for_status()
            models = response.json().get("data", [])
            return any(model.get("id") == self.model_name for model in models if isinstance(model, dict))
        except (requests.RequestException, ValueError, AttributeError):
            return False
