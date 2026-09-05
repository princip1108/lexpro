#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from collections import OrderedDict
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def audit(run_dir: Path, expected_count: int | None = None) -> dict:
    errors: list[str] = []
    summary_path = run_dir / "summary.json"
    if not summary_path.is_file():
        raise FileNotFoundError(f"缺少汇总文件: {summary_path}")
    summary = load_json(summary_path)
    result_paths = sorted((run_dir / "results").glob("*/entities.json"))
    if expected_count is not None and len(result_paths) != expected_count:
        errors.append(f"结果数量应为 {expected_count}，实际为 {len(result_paths)}")
    if summary.get("success_count") != len(result_paths):
        errors.append("summary.success_count 与结果文件数量不一致")
    if summary.get("failure_count") != 0:
        errors.append(f"summary.failure_count={summary.get('failure_count')}")
    failures = run_dir / "failures.jsonl"
    if failures.exists() and failures.read_text(encoding="utf-8").strip():
        errors.append("failures.jsonl 非空")

    totals = {"documents": 0, "sentences": 0, "sentence_entities": 0, "document_entities": 0}
    format_counts: dict[str, int] = {}
    for result_path in result_paths:
        result = load_json(result_path)
        doc_id = result.get("document_id", result_path.parent.name)
        parsed = run_dir / "parsed" / doc_id
        for name in ("raw.json", "content.json", "document.txt"):
            if not (parsed / name).is_file():
                errors.append(f"{doc_id}: 缺少 parsed/{name}")
        if result.get("status") != "success":
            errors.append(f"{doc_id}: status 不是 success")
        file_type = result.get("file_type", "")
        format_counts[file_type] = format_counts.get(file_type, 0) + 1
        if file_type in {"doc", "docx"} and not list((run_dir / "converted" / doc_id).glob("*.pdf")):
            errors.append(f"{doc_id}: 缺少 Word 转换后的 PDF")

        sentences = result.get("sentences", [])
        if [item.get("sentence_id") for item in sentences] != list(range(len(sentences))):
            errors.append(f"{doc_id}: sentence_id 不连续")
        expected_dedup: OrderedDict[tuple[str, str], dict] = OrderedDict()
        for sentence in sentences:
            text = sentence.get("text", "")
            sentence_id = sentence.get("sentence_id")
            for entity in sentence.get("entities", []):
                start, end = entity.get("start"), entity.get("end")
                if not isinstance(start, int) or not isinstance(end, int) or text[start:end] != entity.get("entity"):
                    errors.append(f"{doc_id}: 子句 {sentence_id} 实体位置无效: {entity}")
                key = (entity.get("category"), entity.get("entity"))
                item = expected_dedup.setdefault(key, {
                    "category": key[0], "entity": key[1], "occurrence_count": 0, "sentence_ids": []
                })
                item["occurrence_count"] += 1
                if sentence_id not in item["sentence_ids"]:
                    item["sentence_ids"].append(sentence_id)
        if result.get("document_entities", []) != list(expected_dedup.values()):
            errors.append(f"{doc_id}: document_entities 去重元数据不一致")

        totals["documents"] += 1
        totals["sentences"] += len(sentences)
        totals["sentence_entities"] += sum(len(item.get("entities", [])) for item in sentences)
        totals["document_entities"] += len(result.get("document_entities", []))

    report = {
        "run_dir": str(run_dir),
        **totals,
        "format_counts": format_counts,
        "errors": errors,
        "status": "pass" if not errors else "fail",
    }
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description="审计 LexPro 推理运行目录")
    parser.add_argument("run_dir", type=Path)
    parser.add_argument("--expected-count", type=int)
    args = parser.parse_args()
    run_dir = args.run_dir if args.run_dir.is_absolute() else PROJECT_ROOT / args.run_dir
    report = audit(run_dir.resolve(), args.expected_count)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["status"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main())
