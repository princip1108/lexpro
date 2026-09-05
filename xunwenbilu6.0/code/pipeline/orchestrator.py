from __future__ import annotations

import json
import time
from datetime import datetime
from pathlib import Path
from typing import Any

from .converters import LibreOfficeConverter
from .discovery import document_id
from .preprocessing import normalize_mineru_response
from .response_parser import align_entities, deduplicate_document_entities


class PipelineProcessor:
    def __init__(self, mineru: Any, vllm: Any, output: Any, input_root: Path, converter: Any | None = None) -> None:
        self.mineru = mineru
        self.vllm = vllm
        self.output = output
        self.input_root = Path(input_root).resolve()
        self.converter = converter or LibreOfficeConverter(output.converted_dir)

    def _relative(self, path: Path) -> Path:
        try:
            return path.resolve().relative_to(self.input_root)
        except ValueError:
            return Path(path.name)

    def process(self, paths: list[Path], resume: bool = False, force: bool = False):
        results: list[dict[str, Any]] = []
        failures: list[dict[str, Any]] = []
        for source in paths:
            source = Path(source).resolve()
            relative = self._relative(source)
            doc_id = document_id(relative)
            result_path = self.output.results_dir / doc_id / "entities.json"
            if resume and not force and result_path.exists():
                results.append(json.loads(result_path.read_text(encoding="utf-8")))
                continue
            started = time.perf_counter()
            stage = "prepare"
            try:
                parse_source = source
                if source.suffix.lower() in {".doc", ".docx"}:
                    stage = "word_to_pdf"
                    parse_source = self.converter.convert(source, doc_id)
                stage = "mineru_parse"
                raw = self.mineru.parse(parse_source)
                sentences = normalize_mineru_response(raw)
                self.output.save_parsed(doc_id, raw, sentences)
                stage = "entity_inference"
                predictions = self.vllm.extract([item["text"] for item in sentences])
                for sentence, entities in zip(sentences, predictions):
                    sentence["entities"] = align_entities(sentence["text"], entities)
                result = {
                    "document_id": doc_id,
                    "source_file": relative.as_posix(),
                    "file_type": source.suffix.lower().lstrip("."),
                    "status": "success",
                    "processed_at": datetime.now().astimezone().isoformat(),
                    "elapsed_seconds": round(time.perf_counter() - started, 3),
                    "sentences": sentences,
                    "document_entities": deduplicate_document_entities(sentences),
                }
                self.output.save_result(result)
                results.append(result)
            except Exception as exc:
                failure = {
                    "document_id": doc_id,
                    "source_file": relative.as_posix(),
                    "stage": stage,
                    "error": str(exc),
                    "retry_command": f"bash code/scripts/infer_file.sh '{relative.as_posix()}' --force",
                }
                self.output.record_failure(failure)
                failures.append(failure)
        self.output.write_summary(results, failures)
        return results, failures

