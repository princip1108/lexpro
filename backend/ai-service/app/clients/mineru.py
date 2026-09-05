from __future__ import annotations

from pathlib import Path
from typing import Any

import requests


class MinerUClientError(RuntimeError):
    pass


class MinerUClient:
    def __init__(
        self,
        base_url: str,
        *,
        connect_timeout_seconds: float = 5.0,
        read_timeout_seconds: float = 900.0,
        max_file_bytes: int = 50 * 1024 * 1024,
        session: Any | None = None,
    ) -> None:
        normalized = base_url.rstrip("/")
        self.parse_url = normalized if normalized.endswith("/parse") else f"{normalized}/parse"
        self.connect_timeout_seconds = connect_timeout_seconds
        self.read_timeout_seconds = read_timeout_seconds
        self.max_file_bytes = max_file_bytes
        self.session = session or requests.Session()

    def parse_bytes(self, filename: str, content: bytes, media_type: str) -> dict[str, Any]:
        safe_filename = Path(filename).name
        if not safe_filename or safe_filename in {".", ".."}:
            raise MinerUClientError("INVALID_FILE_NAME")
        if not content:
            raise MinerUClientError("EMPTY_DOCUMENT")
        if len(content) > self.max_file_bytes:
            raise MinerUClientError("DOCUMENT_TOO_LARGE")
        try:
            response = self.session.post(
                self.parse_url,
                files={"file": (safe_filename, content, media_type)},
                timeout=(self.connect_timeout_seconds, self.read_timeout_seconds),
            )
            response.raise_for_status()
            payload = response.json()
        except (requests.RequestException, ValueError) as exception:
            raise MinerUClientError("MINERU_SERVICE_UNAVAILABLE") from exception
        if not isinstance(payload, dict) or payload.get("status") != "success":
            raise MinerUClientError("MINERU_RESPONSE_INVALID")
        if not isinstance(payload.get("data"), (list, dict)):
            raise MinerUClientError("MINERU_RESPONSE_INVALID")
        return payload

    def health(self) -> bool:
        health_url = self.parse_url.removesuffix("/parse") + "/health"
        try:
            response = self.session.get(
                health_url,
                timeout=(self.connect_timeout_seconds, min(self.read_timeout_seconds, 5.0)),
            )
            response.raise_for_status()
            payload = response.json()
            return payload.get("status") == "ok" and payload.get("model_exists") is True
        except (requests.RequestException, ValueError, AttributeError):
            return False

