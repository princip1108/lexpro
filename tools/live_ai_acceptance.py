"""Run non-destructive acceptance checks against tunneled MinerU and LexPro services.

The script sends only the selected document and one built-in fictional sentence. It
prints structural metrics instead of model/document bodies so case text is not
accidentally copied into ordinary terminal logs.
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
AI_SERVICE_ROOT = ROOT / "backend" / "ai-service"
sys.path.insert(0, str(AI_SERVICE_ROOT))

from app.clients.lexpro import ENTITY_LABELS, LexProClient, LexProClientError  # noqa: E402
from app.clients.mineru import MinerUClient, MinerUClientError  # noqa: E402
from app.pipeline import canonical_document_from_mineru  # noqa: E402


DEFAULT_DOCUMENT = ROOT / "legal_llm" / "_mineru_test_判决书.pdf"
FICTIONAL_TEXT = (
    "2026年9月1日，犯罪嫌疑人张某在北京市朝阳区向李某出售海洛因。"
    "北京市公安局以张某涉嫌贩卖毒品罪立案。备注😀𠮷。"
)
EXPECTED = {
    "犯罪嫌疑人": "张某",
    "地名": "北京市朝阳区",
    "组织机构名": "北京市公安局",
    "时间": "2026年9月1日",
    "罪名": "贩卖毒品罪",
    "毒品种类": "海洛因",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="LexPro/MinerU live acceptance")
    parser.add_argument("--mineru-url", default="http://127.0.0.1:13456")
    parser.add_argument("--lexpro-url", default="http://127.0.0.1:8001")
    parser.add_argument("--model", default="LexPro_8B")
    parser.add_argument("--document", type=Path, default=DEFAULT_DOCUMENT)
    parser.add_argument(
        "--strict-six-classes",
        action="store_true",
        help="Fail unless the fictional sentence yields all six expected classes.",
    )
    return parser.parse_args()


def run_mineru(args: argparse.Namespace) -> dict[str, object]:
    path = args.document.resolve()
    if not path.is_file():
        raise RuntimeError(f"TEST_DOCUMENT_NOT_FOUND: {path}")
    media_type = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    client = MinerUClient(args.mineru_url)
    if not client.health():
        raise RuntimeError("MINERU_HEALTH_FAILED")
    payload = client.parse_bytes(path.name, path.read_bytes(), media_type)
    document = canonical_document_from_mineru(payload)
    if not document.text.strip() or not document.blocks:
        raise RuntimeError("MINERU_EMPTY_PARSE_RESULT")
    return {
        "status": "PASS",
        "fileName": path.name,
        "mediaType": media_type,
        "blockCount": len(document.blocks),
        "utf16Length": len(document.text.encode("utf-16-le")) // 2,
        "sha256Present": bool(document.text_sha256),
    }


def run_lexpro(args: argparse.Namespace) -> dict[str, object]:
    client = LexProClient(args.lexpro_url, model_name=args.model, batch_size=1)
    if not client.health():
        raise RuntimeError("LEXPRO_HEALTH_FAILED")
    result = client.extract([FICTIONAL_TEXT])[0]
    invalid = [category for category, _entity in result if category not in ENTITY_LABELS]
    outside_source = [entity for _category, entity in result if entity not in FICTIONAL_TEXT]
    if invalid:
        raise RuntimeError(f"LEXPRO_INVALID_CATEGORIES: {invalid}")
    if outside_source:
        raise RuntimeError("LEXPRO_ENTITY_NOT_IN_SOURCE")
    found = {category for category, entity in result if EXPECTED.get(category) == entity}
    missing = sorted(set(EXPECTED) - found)
    if args.strict_six_classes and missing:
        raise RuntimeError(f"LEXPRO_EXPECTED_ENTITIES_MISSING: {','.join(missing)}")
    return {
        "status": "PASS",
        "model": args.model,
        "entityCount": len(result),
        "categories": sorted({category for category, _entity in result}),
        "exactExpectedClasses": sorted(found),
        "missingExpectedClasses": missing,
        "allEntitiesInSource": True,
        "specialCharacterInputAccepted": True,
    }


def main() -> int:
    args = parse_args()
    report: dict[str, object] = {"mineru": None, "lexpro": None}
    errors: dict[str, str] = {}
    for component, check in (("mineru", run_mineru), ("lexpro", run_lexpro)):
        try:
            report[component] = check(args)
        except (RuntimeError, MinerUClientError, LexProClientError) as error:
            errors[component] = str(error)
            report[component] = {"status": "FAIL", "error": str(error)}
    report["status"] = "FAIL" if errors else "PASS"
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
