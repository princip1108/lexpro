from __future__ import annotations

from pathlib import Path
from typing import Any

import requests


class MinerUClient:
    def __init__(self, api_url: str, timeout: int = 3600, session: Any | None = None) -> None:
        api_url = api_url.rstrip("/")
        self.parse_url = api_url if api_url.endswith("/parse") else f"{api_url}/parse"
        self.timeout = timeout
        self.session = session or requests.Session()

    def parse(self, path: Path) -> dict[str, Any]:
        with Path(path).open("rb") as handle:
            response = self.session.post(
                self.parse_url,
                files={"file": (Path(path).name, handle)},
                timeout=self.timeout,
            )
        response.raise_for_status()
        payload = response.json()
        if payload.get("status") != "success":
            raise RuntimeError(f"MinerU 解析失败: {payload}")
        return payload

