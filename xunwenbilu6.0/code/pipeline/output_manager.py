from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from typing import Any


def _write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


class RunOutput:
    def __init__(self, output_root: Path, run_id: str) -> None:
        self.root = Path(output_root) / "runs" / run_id
        self.converted_dir = self.root / "converted"
        self.parsed_dir = self.root / "parsed"
        self.results_dir = self.root / "results"
        self.logs_dir = self.root / "logs"
        for directory in (self.converted_dir, self.parsed_dir, self.results_dir, self.logs_dir):
            directory.mkdir(parents=True, exist_ok=True)

    def save_parsed(self, document_id: str, raw: Any, sentences: list[dict[str, Any]]) -> None:
        target = self.parsed_dir / document_id
        _write_json(target / "raw.json", raw)
        _write_json(target / "content.json", sentences)
        (target / "document.txt").write_text("\n".join(s["text"] for s in sentences), encoding="utf-8")

    def save_result(self, result: dict[str, Any]) -> Path:
        path = self.results_dir / result["document_id"] / "entities.json"
        _write_json(path, result)
        return path

    def record_failure(self, failure: dict[str, Any]) -> None:
        path = self.root / "failures.jsonl"
        with path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(failure, ensure_ascii=False) + "\n")

    def write_manifest(self, manifest: dict[str, Any]) -> None:
        _write_json(self.root / "manifest.json", manifest)

    def write_summary(self, results: list[dict[str, Any]], failures: list[dict[str, Any]]) -> None:
        formats: dict[str, int] = {}
        total_entities = 0
        for result in results:
            file_type = result["file_type"]
            formats[file_type] = formats.get(file_type, 0) + 1
            total_entities += sum(len(sentence.get("entities", [])) for sentence in result.get("sentences", []))
        summary = {
            "generated_at": datetime.now().astimezone().isoformat(),
            "total_count": len(results) + len(failures),
            "success_count": len(results),
            "failure_count": len(failures),
            "format_counts": formats,
            "total_sentence_entities": total_entities,
            "documents": [
                {
                    "document_id": item["document_id"],
                    "source_file": item["source_file"],
                    "status": item["status"],
                    "sentence_count": len(item.get("sentences", [])),
                    "document_entity_count": len(item.get("document_entities", [])),
                }
                for item in results
            ],
        }
        _write_json(self.root / "summary.json", summary)
        failures_path = self.root / "failures.jsonl"
        failures_path.write_text(
            "".join(json.dumps(item, ensure_ascii=False) + "\n" for item in failures),
            encoding="utf-8",
        )
