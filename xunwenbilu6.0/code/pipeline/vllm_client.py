from __future__ import annotations

from typing import Any

import requests

from .prompts import create_full_prompt
from .response_parser import parse_llm_response


class VLLMClient:
    def __init__(
        self,
        api_url: str,
        model_name: str = "LexPro_8B",
        batch_size: int = 128,
        timeout: int = 3600,
        session: Any | None = None,
    ) -> None:
        if batch_size < 1:
            raise ValueError("batch_size 必须大于 0")
        self.api_url = api_url.rstrip("/")
        self.model_name = model_name
        self.batch_size = batch_size
        self.timeout = timeout
        self.session = session or requests.Session()

    def extract(self, texts: list[str]) -> list[list[dict[str, str]]]:
        results: list[list[dict[str, str]]] = []
        for offset in range(0, len(texts), self.batch_size):
            batch = texts[offset:offset + self.batch_size]
            payload = {
                "model": self.model_name,
                "prompt": [create_full_prompt(text) for text in batch],
                "temperature": 0.15,
                "top_p": 0.9,
                "max_tokens": 1024,
                "seed": 42,
                "stop": ["<Labeled End>", "</s>"],
            }
            response = self.session.post(f"{self.api_url}/v1/completions", json=payload, timeout=self.timeout)
            response.raise_for_status()
            choices = sorted(response.json().get("choices", []), key=lambda item: item.get("index", 0))
            if len(choices) != len(batch):
                raise RuntimeError(f"vLLM 返回数量异常: 期望 {len(batch)}，实际 {len(choices)}")
            results.extend(parse_llm_response(choice.get("text", "")) for choice in choices)
        return results

