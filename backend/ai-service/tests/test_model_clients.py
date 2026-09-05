import unittest
import io
import zipfile

from app.clients.lexpro import LexProClient, LexProClientError, parse_completion
from app.clients.mineru import MinerUClient, MinerUClientError
from app.pipeline import canonical_document_from_mineru, recognize_document
from app.word import parse_docx, WordDocumentError
from app.unicode_offsets import slice_utf16


def word_fixture(body, extras=None):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as package:
        document_xml = (
            '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" '
            'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" '
            'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><w:body>'
            + body + '</w:body></w:document>').encode("utf-8")
        for name, data in {"word/document.xml": document_xml, **(extras or {})}.items():
            package.writestr(name, data)
    return output.getvalue()


class FakeResponse:
    def __init__(self, payload, *, status_error=None):
        self.payload = payload
        self.status_error = status_error

    def raise_for_status(self):
        if self.status_error:
            raise self.status_error

    def json(self):
        return self.payload


class FakeSession:
    def __init__(self, *, post_responses=(), get_responses=()):
        self.post_responses = list(post_responses)
        self.get_responses = list(get_responses)
        self.posts = []
        self.gets = []

    def post(self, url, **kwargs):
        self.posts.append((url, kwargs))
        return self.post_responses.pop(0)

    def get(self, url, **kwargs):
        self.gets.append((url, kwargs))
        return self.get_responses.pop(0)


class ModelClientTests(unittest.TestCase):
    def test_word_non_text_images_preserve_body_but_never_hide_provider_failures(self):
        image = '<w:p><w:r><a:blip r:embed="img"/></w:r></w:p>'
        extras = {'word/_rels/document.xml.rels': '<Relationships><Relationship Id="img" Target="media/a.png"/></Relationships>',
                  'word/media/a.png': b'image'}
        body = '<w:p><w:r><w:t>正文😀𠮷</w:t></w:r></w:p>'
        for items in ([], [{'type': 'image'}], [{'type': 'text', 'text': ' \n'}]):
            with self.subTest(items=items):
                client = MinerUClient('http://mineru', session=FakeSession(post_responses=(FakeResponse({'status': 'success', 'data': items}),)))
                warnings = []
                document = parse_docx(word_fixture(body + image + image, extras), client, warnings=warnings)
                self.assertEqual('正文😀𠮷', document.text)
                self.assertEqual([{'code': 'WORD_IMAGE_NO_TEXT', 'imageIndex': 1},
                                  {'code': 'WORD_IMAGE_NO_TEXT', 'imageIndex': 2}], warnings)
                self.assertEqual(1, len(client.session.posts))
        client = MinerUClient('http://mineru', session=FakeSession(post_responses=(FakeResponse({'status': 'success', 'data': []}),)))
        with self.assertRaisesRegex(WordDocumentError, 'WORD_DOCUMENT_EMPTY'):
            parse_docx(word_fixture(image, extras), client)
        for payload in ({'status': 'failed', 'data': []}, {'status': 'success', 'data': {}},
                        {'status': 'success', 'data': [None]}, {'status': 'success', 'data': [{'type': 'text'}]}):
            client = MinerUClient('http://mineru', session=FakeSession(post_responses=(FakeResponse(payload),)))
            with self.subTest(payload=payload), self.assertRaises(MinerUClientError):
                parse_docx(word_fixture(body + image, extras), client)
        import requests
        for failure in (requests.Timeout(), requests.ConnectionError(), requests.HTTPError()):
            client = MinerUClient('http://mineru', session=FakeSession(post_responses=(FakeResponse({}, status_error=failure),)))
            with self.subTest(failure=type(failure)), self.assertRaisesRegex(MinerUClientError, 'MINERU_SERVICE_UNAVAILABLE'):
                parse_docx(word_fixture(body + image, extras), client)

    def test_word_preserves_unicode_tables_and_inline_image_order(self):
        content = word_fixture(
            '<w:p><w:r><w:t>张😀𠮷 &amp; “询问”</w:t><w:tab/><w:t>一</w:t><w:br/><w:t>二</w:t>'
            '<a:blip r:embed="img"/><w:t>图片后</w:t></w:r></w:p>'
            '<w:tbl><w:tr><w:tc><w:p><w:r><w:t>金额</w:t></w:r></w:p></w:tc>'
            '<w:tc><w:p><w:r><w:t>12345.67元</w:t></w:r></w:p></w:tc></w:tr></w:tbl>',
            {'word/_rels/document.xml.rels': '<Relationships><Relationship Id="img" Target="media/a.png"/></Relationships>',
             'word/media/a.png': b'image'})
        client = MinerUClient('http://mineru', session=FakeSession(post_responses=(FakeResponse({
            'status': 'success', 'data': [{'type': 'text', 'text': '虚构图片文字', 'page_idx': 0}]
        }),)))
        document = parse_docx(content, client)
        self.assertEqual('张😀𠮷 & “询问”\t一\n二\n\n虚构图片文字\n\n图片后\n\n金额\n\n12345.67元', document.text)
        for block in document.blocks:
            self.assertEqual(block.text, slice_utf16(document.text, block.global_start_utf16, block.global_end_utf16))
            self.assertIsNone(block.page_no)
            self.assertIsNone(block.bbox)

    def test_word_rejects_corrupt_dtd_external_images_and_large_packages(self):
        client = MinerUClient('http://mineru', session=FakeSession())
        for content, code in [
            (b'not-a-docx', 'WORD_DOCUMENT_INVALID'),
            (word_fixture('<w:p/>'), 'WORD_DOCUMENT_EMPTY'),
            (word_fixture('<a:blip r:embed="img"/>', {'word/_rels/document.xml.rels':
                '<Relationships><Relationship Id="img" Target="https://example.invalid/a.png" TargetMode="External"/></Relationships>'}), 'WORD_EXTERNAL_CONTENT_UNSUPPORTED'),
            (word_fixture('<w:p/>', {'word/document.xml': b'<!DOCTYPE test [<!ENTITY x "secret">]><test>&x;</test>'}), 'WORD_DOCUMENT_INVALID'),
            (word_fixture('<w:p/>', {'word/huge.xml': b'x' * (65 * 1024 * 1024)}), 'WORD_DOCUMENT_TOO_LARGE'),
        ]:
            with self.subTest(code=code), self.assertRaisesRegex(WordDocumentError, code):
                parse_docx(content, client)
        self.assertEqual([], client.session.posts)

    def test_plain_word_does_not_require_mineru(self):
        client = MinerUClient('http://mineru', session=FakeSession())
        self.assertEqual('虚构笔录', parse_docx(word_fixture('<w:p><w:r><w:t>虚构笔录</w:t></w:r></w:p>'), client).text)
        self.assertEqual([], client.session.posts)

    def test_deployed_single_line_format_and_unrecognized_output(self):
        self.assertEqual((("时间", "2026年9月1日"), ("犯罪嫌疑人", "张某")),
                         parse_completion("#### 时间：2026年9月1日\n#### 犯罪嫌疑人：张某\n"))
        for invalid in ("无法按要求提取", "", "\n\t", "<Labeled End>", "#### None\n无法按要求提取"):
            with self.subTest(invalid=invalid), self.assertRaisesRegex(LexProClientError, "LEXPRO_RESPONSE_FORMAT_INVALID"):
                parse_completion(invalid)
        for empty in ("#### None", "#### None\n" * 6,
                      "\r\n #### None \r\n\r\nnone\r\n#### NONE\n<Labeled End>"):
            with self.subTest(empty=empty):
                self.assertEqual((), parse_completion(empty))
        self.assertEqual((("地名", "北京"),), parse_completion("#### None\n#### 地名：北京\n#### None"))
        self.assertEqual((("时间", "2026年9月1日"), ("犯罪嫌疑人", "张某"),
                          ("地名", "北京"), ("毒品种类", "海洛因")),
                         parse_completion("#### time：2026年9月1日\n#### suspect：张某\n"
                                          "#### location：北京\n#### drug：海洛因\n"))
        self.assertEqual((("组织机构名", "公安局"), ("罪名", "贩卖毒品罪")),
                         parse_completion("#### category：ORGANIZATION\n#### entity：公安局\n"
                                          "#### crime：贩卖毒品罪"))
        self.assertEqual((), parse_completion("#### amount：123元"))
        self.assertEqual((), parse_completion(("#### None\n" * 200) + "####"))

    def test_mineru_uses_real_multipart_contract(self):
        session = FakeSession(post_responses=(FakeResponse({"status": "success", "data": []}),))
        payload = MinerUClient("http://127.0.0.1:13456", session=session).parse_bytes(
            "../询问笔录.pdf", b"pdf", "application/pdf"
        )
        self.assertEqual("success", payload["status"])
        url, request = session.posts[0]
        self.assertEqual("http://127.0.0.1:13456/parse", url)
        self.assertEqual("询问笔录.pdf", request["files"]["file"][0])

    def test_mineru_rejects_empty_and_oversized_files_before_network(self):
        client = MinerUClient("http://mineru", max_file_bytes=2, session=FakeSession())
        with self.assertRaisesRegex(MinerUClientError, "EMPTY_DOCUMENT"):
            client.parse_bytes("a.pdf", b"", "application/pdf")
        with self.assertRaisesRegex(MinerUClientError, "DOCUMENT_TOO_LARGE"):
            client.parse_bytes("a.pdf", b"123", "application/pdf")

    def test_completion_parser_ignores_categories_outside_the_six_class_contract(self):
        self.assertEqual((("犯罪嫌疑人", "张某"),), parse_completion(
            "#### category：犯罪嫌疑人\n#### entity：张某\n"
            "#### category：金额\n#### entity：100元\n<Labeled End>"
            ))

    def test_lexpro_batches_real_completion_contract(self):
        session = FakeSession(
            post_responses=(
                FakeResponse({"choices": [
                    {"index": 1, "text": "#### None\n" * 6},
                    {"index": 0, "text": "#### category：地名\n#### entity：北京"},
                ]}),
            )
        )
        results = LexProClient("http://127.0.0.1:8001", batch_size=2, session=session).extract(
            ("张某在北京", "没有实体")
        )
        self.assertEqual((("地名", "北京"),), results[0])
        self.assertEqual((), results[1])
        url, request = session.posts[0]
        self.assertEqual("http://127.0.0.1:8001/v1/completions", url)
        self.assertEqual("LexPro_8B", request["json"]["model"])
        self.assertEqual(2, len(request["json"]["prompt"]))

    def test_lexpro_rejects_missing_batch_choices(self):
        session = FakeSession(post_responses=(FakeResponse({"choices": [{"index": 0, "text": "#### None"}]}),))
        with self.assertRaisesRegex(LexProClientError, "LEXPRO_RESPONSE_COUNT_MISMATCH"):
            LexProClient("http://lexpro", session=session).extract(("一", "二"))

    def test_real_mineru_shape_preserves_page_and_bbox(self):
        document = canonical_document_from_mineru({
            "status": "success",
            "data": [
                {"type": "text", "text": "张😀", "bbox": [1, 2, 3, 4], "page_idx": 0},
                {"type": "image", "img_path": "ignored.png", "page_idx": 0},
                {"type": "text", "text": "北京", "bbox": [5, 6, 7, 8], "page_idx": 1},
            ],
        })
        self.assertEqual("张😀\n\n北京", document.text)
        self.assertEqual((1, 2), tuple(block.page_no for block in document.blocks))
        self.assertEqual((5, 7), (
            document.blocks[1].global_start_utf16,
            document.blocks[1].global_end_utf16,
        ))

    def test_pipeline_returns_document_offsets_and_deduplication(self):
        document = canonical_document_from_mineru({
            "status": "success",
            "data": [
                {"type": "text", "text": "张𠮷在北京", "page_idx": 0},
                {"type": "text", "text": "张某也在北京", "page_idx": 0},
            ],
        })
        session = FakeSession(post_responses=(FakeResponse({"choices": [
            {"index": 0, "text": "#### category：地名\n#### entity：北京"},
            {"index": 1, "text": "#### category：地名\n#### entity：北京"},
        ]}),))
        result = recognize_document(document, LexProClient("http://lexpro", session=session))
        self.assertEqual("UTF16_CODE_UNIT", result["offsetUnit"])
        self.assertEqual((4, 6), (
            result["entities"][0]["globalStartUtf16"],
            result["entities"][0]["globalEndUtf16"],
        ))
        self.assertEqual(2, result["documentEntities"][0]["occurrenceCount"])


if __name__ == "__main__":
    unittest.main()
