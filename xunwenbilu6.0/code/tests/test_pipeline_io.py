import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


CODE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CODE_ROOT))

from pipeline.output_manager import RunOutput
from pipeline.converters import LibreOfficeConverter
from pipeline.vllm_client import VLLMClient


class FakeResponse:
    def __init__(self, payload, status_code=200):
        self.payload = payload
        self.status_code = status_code
        self.text = json.dumps(payload, ensure_ascii=False)

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(self.text)

    def json(self):
        return self.payload


class FakeSession:
    def __init__(self):
        self.calls = []

    def post(self, url, json=None, timeout=None):
        self.calls.append({"url": url, "json": json, "timeout": timeout})
        choices = [{"index": i, "text": "#### None\n<Labeled End>"} for i, _ in enumerate(json["prompt"])]
        return FakeResponse({"choices": choices})


class VLLMClientTests(unittest.TestCase):
    def test_extracts_one_document_in_sentence_batches(self):
        session = FakeSession()
        client = VLLMClient("http://127.0.0.1:8001", "LexPro_8B", batch_size=2, session=session)

        result = client.extract(["句子1", "句子2", "句子3"])

        self.assertEqual(result, [[], [], []])
        self.assertEqual([len(call["json"]["prompt"]) for call in session.calls], [2, 1])
        self.assertTrue(all(call["url"].endswith("/v1/completions") for call in session.calls))


class ConverterTests(unittest.TestCase):
    def test_word_conversion_keeps_profile_and_pdf_in_run_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "input.docx"
            source.write_bytes(b"docx")
            converted = root / "converted"

            def fake_run(command, **kwargs):
                outdir = Path(command[command.index("--outdir") + 1])
                (outdir / "input.pdf").write_bytes(b"pdf")
                return type("Completed", (), {"returncode": 0, "stdout": "", "stderr": ""})()

            with patch("pipeline.converters.subprocess.run", side_effect=fake_run) as mocked:
                result = LibreOfficeConverter(converted, executable="libreoffice").convert(source, "doc-1")

            command = mocked.call_args.args[0]
            profile_arg = next(value for value in command if value.startswith("-env:UserInstallation="))
            self.assertTrue(profile_arg.startswith(f"-env:UserInstallation={(converted / 'doc-1').resolve().as_uri()}"))
            self.assertEqual(result, converted / "doc-1" / "input.pdf")
            self.assertEqual(list((converted / "doc-1").glob("lo_profile_*")), [])


class OutputTests(unittest.TestCase):
    def test_saves_sentence_and_empty_document_entities(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = RunOutput(Path(tmp), "run-1")
            result = {
                "document_id": "doc-1",
                "source_file": "input.pdf",
                "file_type": "pdf",
                "status": "success",
                "sentences": [{"sentence_id": 0, "text": "无实体文本", "entities": []}],
                "document_entities": [],
            }

            path = output.save_result(result)
            output.write_summary([result], [])

            self.assertEqual(json.loads(path.read_text(encoding="utf-8"))["document_entities"], [])
            summary = json.loads((output.root / "summary.json").read_text(encoding="utf-8"))
            self.assertEqual(summary["success_count"], 1)
            self.assertEqual(summary["failure_count"], 0)

    def test_completed_retry_rewrites_stale_failure_log(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = RunOutput(Path(tmp), "retry-run")
            output.record_failure({"source_file": "input.pdf", "error": "old error"})

            output.write_summary([], [])

            self.assertEqual((output.root / "failures.jsonl").read_text(encoding="utf-8"), "")


if __name__ == "__main__":
    unittest.main()
