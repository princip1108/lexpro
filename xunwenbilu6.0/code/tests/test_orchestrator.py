import sys
import tempfile
import unittest
from pathlib import Path


CODE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CODE_ROOT))

from pipeline.orchestrator import PipelineProcessor
from pipeline.output_manager import RunOutput


class FakeMinerU:
    def __init__(self):
        self.calls = []

    def parse(self, path):
        self.calls.append(Path(path).name)
        return {"status": "success", "data": [
            {"type": "text", "text": f"{Path(path).stem}第一句"},
            {"type": "text", "text": f"{Path(path).stem}第二句"},
        ]}


class FakeVLLM:
    def __init__(self):
        self.document_batches = []

    def extract(self, texts):
        self.document_batches.append(list(texts))
        return [[{"category": "地名", "entity": text[:1]}] for text in texts]


class OrchestratorTests(unittest.TestCase):
    def test_processes_documents_sequentially_without_mixing_sentences(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            first = root / "a.pdf"
            second = root / "b.png"
            first.write_bytes(b"a")
            second.write_bytes(b"b")
            mineru = FakeMinerU()
            vllm = FakeVLLM()
            output = RunOutput(root / "output", "test")
            processor = PipelineProcessor(mineru, vllm, output, root)

            results, failures = processor.process([first, second])

            self.assertEqual(failures, [])
            self.assertEqual(mineru.calls, ["a.pdf", "b.png"])
            self.assertEqual(len(vllm.document_batches), 2)
            self.assertTrue(all(text.startswith("a") for text in vllm.document_batches[0]))
            self.assertTrue(all(text.startswith("b") for text in vllm.document_batches[1]))
            self.assertEqual(results[0]["document_entities"][0]["occurrence_count"], 2)


if __name__ == "__main__":
    unittest.main()
