import json
import sys
import tempfile
import unittest
from pathlib import Path


CODE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CODE_ROOT))

from pipeline.discovery import collect_documents, document_id, portable_path
from pipeline.preprocessing import normalize_mineru_response
from pipeline.prompts import create_full_prompt
from pipeline.response_parser import align_entities, deduplicate_document_entities, parse_llm_response


class DiscoveryTests(unittest.TestCase):
    def test_collect_documents_recursively_in_stable_order(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "b.pdf").write_bytes(b"pdf")
            (root / "nested").mkdir()
            (root / "nested" / "a.DOCX").write_bytes(b"docx")
            (root / "ignored.txt").write_text("ignore", encoding="utf-8")

            files = collect_documents(root, recursive=True)

            self.assertEqual([p.relative_to(root).as_posix() for p in files], ["b.pdf", "nested/a.DOCX"])

    def test_document_id_distinguishes_same_name_in_different_directories(self):
        first = document_id(Path("case-a/record.pdf"))
        second = document_id(Path("case-b/record.pdf"))
        self.assertNotEqual(first, second)
        self.assertTrue(first.startswith("record-"))

    def test_portable_path_prefers_project_relative_path(self):
        base = Path("/srv/xunwenbilu6.0")
        self.assertEqual(portable_path(base / "data" / "a.pdf", base), "data/a.pdf")


class PreprocessingTests(unittest.TestCase):
    def test_normalize_mineru_response_keeps_only_non_empty_text_blocks(self):
        response = {
            "status": "success",
            "data": [
                {"type": "text", "text": " 第一段 "},
                {"type": "image", "text": "图片说明"},
                {"type": "text", "text": ""},
                "第二段",
            ],
        }

        sentences = normalize_mineru_response(response)

        self.assertEqual(sentences, [
            {"sentence_id": 0, "text": "第一段", "entities": []},
            {"sentence_id": 1, "text": "第二段", "entities": []},
        ])


class PromptTests(unittest.TestCase):
    def test_prompt_keeps_the_validated_wording_and_examples(self):
        prompt = create_full_prompt("测试文本")
        self.assertIn("从询问笔录中的问答对中提出指定类别的实体", prompt)
        self.assertIn("示例1：", prompt)
        self.assertIn("如果无实体示例2：", prompt)
        self.assertTrue(prompt.endswith("## 文本内容:\n测试文本\n## 提取结果:\n"))


class ResponseParserTests(unittest.TestCase):
    def test_parse_and_align_entities(self):
        raw = """#### category：犯罪嫌疑人
#### entity：张三
#### category：时间
#### entity：2025年6月1日
<Labeled End>"""
        parsed = parse_llm_response(raw)
        aligned = align_entities("张三于2025年6月1日接受询问。", parsed)

        self.assertEqual(parsed, [
            {"category": "犯罪嫌疑人", "entity": "张三"},
            {"category": "时间", "entity": "2025年6月1日"},
        ])
        self.assertEqual(aligned[0]["start"], 0)
        self.assertEqual(aligned[0]["end"], 2)
        self.assertEqual(aligned[1]["start"], 3)

    def test_empty_model_response_is_valid(self):
        self.assertEqual(parse_llm_response("#### None\n<Labeled End>"), [])

    def test_parse_single_line_category_entity_variant(self):
        raw = "category：罪名 entity：盗窃罪\n<Labeled End>"
        self.assertEqual(parse_llm_response(raw), [{"category": "罪名", "entity": "盗窃罪"}])

    def test_document_entities_are_deduplicated_with_occurrence_metadata(self):
        sentences = [
            {"sentence_id": 0, "entities": [{"category": "地名", "entity": "惠州", "start": 0, "end": 2}]},
            {"sentence_id": 1, "entities": [
                {"category": "地名", "entity": "惠州", "start": 3, "end": 5},
                {"category": "罪名", "entity": "盗窃罪", "start": 6, "end": 9},
            ]},
        ]

        result = deduplicate_document_entities(sentences)

        self.assertEqual(result, [
            {"category": "地名", "entity": "惠州", "occurrence_count": 2, "sentence_ids": [0, 1]},
            {"category": "罪名", "entity": "盗窃罪", "occurrence_count": 1, "sentence_ids": [1]},
        ])


if __name__ == "__main__":
    unittest.main()
