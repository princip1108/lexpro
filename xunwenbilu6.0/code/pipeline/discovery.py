from __future__ import annotations

import hashlib
import re
from pathlib import Path
from typing import Iterable


SUPPORTED_EXTENSIONS = {".pdf", ".png", ".jpg", ".jpeg", ".doc", ".docx"}


def is_supported(path: Path) -> bool:
    return path.is_file() and path.suffix.lower() in SUPPORTED_EXTENSIONS


def collect_documents(input_dir: Path, recursive: bool = True) -> list[Path]:
    input_dir = Path(input_dir).resolve()
    if not input_dir.is_dir():
        raise NotADirectoryError(f"输入目录不存在: {input_dir}")
    candidates: Iterable[Path] = input_dir.rglob("*") if recursive else input_dir.glob("*")
    return sorted((p for p in candidates if is_supported(p)), key=lambda p: p.relative_to(input_dir).as_posix().casefold())


def document_id(relative_path: Path) -> str:
    normalized = Path(relative_path).as_posix()
    stem = re.sub(r"[^0-9A-Za-z_\-\u4e00-\u9fff]+", "_", Path(relative_path).stem).strip("_") or "document"
    digest = hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:10]
    return f"{stem}-{digest}"


def portable_path(path: Path, base: Path) -> str:
    try:
        return Path(path).relative_to(Path(base)).as_posix()
    except ValueError:
        return Path(path).as_posix()
