"""Preview and migrate legal_llm SQLite data through LexPro's public APIs.

The source database is always opened read-only. Mutating commands refuse non-loopback
API targets. Credentials are read from environment variables and never written to disk.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sqlite3
import sys
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse

import requests


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DB = ROOT / "legal_llm" / "data" / "legal.db"
REPORT_DIR = ROOT / "backups" / "legal_llm_migration"
ARRAY_FIELDS = ("casecausefull", "disputefocus", "applicable_law", "keywords")


def text(value, limit=None):
    value = None if value is None else str(value).replace("\r\n", "\n").replace("\r", "\n").strip()
    if not value:
        return None
    return value[:limit] if limit else value


def string_list(value):
    value = text(value)
    if not value:
        return []
    try:
        parsed = json.loads(value)
        values = parsed if isinstance(parsed, list) else [parsed]
    except json.JSONDecodeError:
        values = re.split(r"[、,，;；|\n]+", value)
    return list(dict.fromkeys(item for raw in values if (item := text(raw, 500))))


def iso_date(value):
    value = text(value)
    if not value:
        return None
    normalized = value.replace("年", "-").replace("月", "-").replace("日", "")
    normalized = normalized.replace("/", "-").replace(".", "-")
    for fmt in ("%Y-%m-%d", "%Y-%m", "%Y%m%d"):
        try:
            parsed = datetime.strptime(normalized, fmt)
            return parsed.date().isoformat() if fmt != "%Y-%m" else None
        except ValueError:
            pass
    return None


def external_id(row):
    case_id = text(row["caseid"], 80)
    if case_id:
        # Source case numbers are not unique: 27 duplicated groups contain different records.
        # Keep the source row id so all 6,786 rows remain distinct and reruns stay idempotent.
        return f"legal_llm:{case_id}:{row['id']}"
    material = "\u241f".join(text(row[key]) or "" for key in ("title", "court", "judgedate", "casecause", "fact"))
    return "legal_llm:sha256:" + hashlib.sha256(material.encode("utf-8")).hexdigest()[:64]


def joined_content(row):
    parts = []
    for label, key in (
        ("案例摘要", "summary"), ("案件事实", "fact"), ("检察过程", "prosecutorial_process"),
        ("裁判结果", "adjudication_result"), ("裁判理由", "reasoning"),
        ("典型意义", "guiding_significance"),
    ):
        if value := text(row[key]):
            parts.append(f"【{label}】\n{value}")
    return "\n\n".join(parts)


def case_payload(row):
    title = text(row["title"], 255)
    if not title:
        raise ValueError("blank title")
    source_file = Path(text(row["source_file"]) or "").name or None
    source_url = text(row["source_url"], 2048)
    if source_url and urlparse(source_url).scheme not in ("http", "https"):
        source_url = None
    return {
        "externalCaseId": external_id(row), "title": title,
        "caseCause": text(row["casecause"], 255), "caseCauses": string_list(row["casecausefull"])[:50],
        "caseType": text(row["casetype"], 50), "country": text(row["country"], 100),
        "court": text(row["court"], 255), "courtLevel": text(row["courtlevel"], 50),
        "docType": text(row["doctype"], 50), "disputeFocus": string_list(row["disputefocus"])[:100],
        "judgmentDate": iso_date(row["judgedate"]), "procedure": text(row["procedure"], 100),
        "applicableLaws": string_list(row["applicable_law"])[:100], "caseLevel": text(row["caselevel"], 50),
        "sourceName": text(row["source"], 255), "sourceFile": source_file,
        "sourceUrl": source_url, "keywords": string_list(row["keywords"])[:100],
        "content": text(joined_content(row), 500000), "fact": text(row["fact"], 200000),
        "summary": text(row["summary"], 200000),
        "prosecutorialProcess": text(row["prosecutorial_process"], 200000),
        "adjudicationResult": text(row["adjudication_result"], 200000),
        "reasoning": text(row["reasoning"], 200000),
        "guidingSignificance": text(row["guiding_significance"], 200000),
    }


def connection(path):
    conn = sqlite3.connect(f"file:{path.as_posix()}?mode=ro", uri=True)
    conn.row_factory = sqlite3.Row
    return conn


def resolve_dossier_file(row):
    name = Path(row["file_name"]).name
    candidates = [ROOT / "legal_llm" / "data" / "samples" / name]
    match = re.search(r"/data/(.+)$", (row["file_path"] or "").replace("\\", "/"))
    if match:
        candidates.append(ROOT / "legal_llm" / "data" / Path(match.group(1)))
    return next((path for path in candidates if path.is_file()), None)


def preview(db_path):
    accepted, rejected, invalid_dates = [], [], []
    with connection(db_path) as conn:
        for row in conn.execute("SELECT * FROM case_base ORDER BY id"):
            try:
                accepted.append(case_payload(row))
                if text(row["judgedate"]) and not iso_date(row["judgedate"]):
                    invalid_dates.append({"id": row["id"], "value": row["judgedate"]})
            except Exception as exc:
                rejected.append({"id": row["id"], "reason": str(exc)})
        files = []
        for row in conn.execute("SELECT * FROM dossier_files ORDER BY id"):
            resolved = resolve_dossier_file(row)
            files.append({"caseId": row["case_id"], "fileName": row["file_name"],
                          "found": bool(resolved), "resolvedPath": str(resolved) if resolved else None})
        case_count = conn.execute("SELECT count(*) FROM cases").fetchone()[0]
    duplicates = len(accepted) - len({item["externalCaseId"] for item in accepted})
    return {"sourceDatabase": str(db_path), "typicalCases": len(accepted) + len(rejected),
            "accepted": len(accepted), "rejected": rejected, "duplicateExternalIds": duplicates,
            "invalidDates": invalid_dates, "demoCases": case_count, "dossierFiles": files}


class Api:
    def __init__(self, base_url, username=None, password=None, token=None):
        parsed = urlparse(base_url)
        if parsed.scheme != "http" or parsed.hostname not in ("127.0.0.1", "localhost", "::1"):
            raise SystemExit("Mutating migration only permits a loopback HTTP API target")
        self.base = base_url.rstrip("/")
        if token:
            self.headers = {"Authorization": "Bearer " + token}
        else:
            response = requests.post(self.base + "/api/v1/auth/login",
                                     json={"username": username, "password": password}, timeout=15)
            response.raise_for_status()
            self.headers = {"Authorization": "Bearer " + response.json()["accessToken"]}

    def post_json(self, path, payload, timeout=120):
        response = requests.post(self.base + path, json=payload, headers=self.headers, timeout=timeout)
        self._raise(response)
        return response.json()

    def get_json(self, path, params=None):
        response = requests.get(self.base + path, params=params, headers=self.headers, timeout=30)
        self._raise(response)
        return response.json()

    def upload(self, case_id, path):
        with path.open("rb") as stream:
            response = requests.post(self.base + f"/api/v1/cases/{case_id}/dossier/files",
                                     files={"file": (path.name, stream)}, headers=self.headers, timeout=120)
        self._raise(response)
        return response.json()

    @staticmethod
    def _raise(response):
        if response.ok:
            return
        try:
            problem = response.json()
            detail = problem.get("errorCode") or problem.get("detail") or str(problem)
        except (ValueError, AttributeError):
            detail = response.text[:500]
        raise RuntimeError(f"LexPro API {response.status_code}: {detail}")


def migrate_typical_cases(db_path, api, limit=None):
    migrated, rejected, completed_batches = 0, [], []
    with connection(db_path) as conn:
        rows = conn.execute("SELECT * FROM case_base ORDER BY id").fetchall()
    payloads = []
    for row in rows[:limit]:
        try:
            payloads.append(case_payload(row))
        except Exception as exc:
            rejected.append({"id": row["id"], "reason": str(exc)})
    for offset in range(0, len(payloads), 50):
        batch = payloads[offset:offset + 50]
        try:
            api.post_json("/api/v1/typical-cases/imports", {"cases": batch}, timeout=900)
        except Exception as exc:
            failure = {"offset": offset, "externalCaseIds": [item["externalCaseId"] for item in batch],
                       "reason": str(exc)}
            rejected.append(failure)
            break
        migrated += len(batch)
        completed_batches.append({"offset": offset, "count": len(batch),
                                  "firstExternalCaseId": batch[0]["externalCaseId"]})
        print(f"typical_cases migrated={migrated}/{len(payloads)}", flush=True)
    return {"requested": len(payloads), "migrated": migrated,
            "completedBatches": completed_batches, "rejected": rejected}


def migrate_demo_cases(db_path, api):
    results = []
    with connection(db_path) as conn:
        cases = conn.execute("SELECT * FROM cases ORDER BY id").fetchall()
        files = {row["case_id"]: row for row in conn.execute("SELECT * FROM dossier_files ORDER BY id")}
    for row in cases:
        deadline = iso_date(row["deadline_date"])
        case_no = text(row["case_no"], 100)
        existing = api.get_json("/api/v1/cases", {"keyword": case_no, "page": 1, "size": 100})
        created = next((item for item in existing["items"] if item.get("caseNo") == case_no), None)
        if not created:
            created = api.post_json("/api/v1/cases", {
                "caseName": text(row["case_name"], 255), "caseNo": case_no,
                "caseType": text(row["case_type"], 50) or "刑事", "caseCause": None,
                "caseSource": "legal_llm demo migration", "currentStage": None,
                "acceptDate": iso_date(row["accept_date"]),
                "deadlineAt": deadline + "T23:59:59+08:00" if deadline else None,
            })
        result = {"sourceCaseId": row["id"], "targetCaseId": created["caseId"], "file": None}
        if source_file := files.get(row["id"]):
            path = resolve_dossier_file(source_file)
            current_files = api.get_json(f"/api/v1/cases/{created['caseId']}/dossier/files")
            already_uploaded = next((item for item in current_files if item.get("fileName") == source_file["file_name"]), None)
            result["file"] = (already_uploaded or api.upload(created["caseId"], path)) if path else {"missing": source_file["file_name"]}
        results.append(result)
    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("preview", "pilot", "full", "demo"))
    parser.add_argument("--db", type=Path, default=DEFAULT_DB)
    parser.add_argument("--api", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    if args.command == "preview":
        result = preview(args.db)
    else:
        token = os.environ.get("LEXPRO_MIGRATION_TOKEN")
        username = os.environ.get("LEXPRO_MIGRATION_USERNAME")
        password = os.environ.get("LEXPRO_MIGRATION_PASSWORD")
        if not token and (not username or not password):
            raise SystemExit("LEXPRO_MIGRATION_TOKEN or migration username/password are required")
        api = Api(args.api, username, password, token)
        result = (migrate_typical_cases(args.db, api, 10) if args.command == "pilot" else
                  migrate_typical_cases(args.db, api) if args.command == "full" else
                  migrate_demo_cases(args.db, api))
    report = REPORT_DIR / f"{args.command}_{stamp}.json"
    report.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"report": str(report), "result": result}, ensure_ascii=True))


if __name__ == "__main__":
    main()
