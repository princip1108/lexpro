from __future__ import annotations

from typing import Any


def _content_list(response: Any) -> list[Any]:
    if isinstance(response, dict):
        if response.get("status") not in (None, "success"):
            raise ValueError(f"MinerU 返回失败状态: {response}")
        data = response.get("data", response)
    else:
        data = response
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for key in ("content_list", "blocks", "pages", "elements"):
            value = data.get(key)
            if isinstance(value, list):
                return value
        for value in data.values():
            if isinstance(value, list):
                return value
    return []


def normalize_mineru_response(response: Any) -> list[dict[str, Any]]:
    sentences: list[dict[str, Any]] = []
    for item in _content_list(response):
        text = ""
        if isinstance(item, str):
            text = item.strip()
        elif isinstance(item, dict):
            if item.get("type") == "text" or ("type" not in item and "text" in item):
                text = str(item.get("text", "")).strip()
        if text:
            sentences.append({"sentence_id": len(sentences), "text": text, "entities": []})
    return sentences

