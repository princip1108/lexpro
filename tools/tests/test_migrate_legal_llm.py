from tools.migrate_legal_llm import case_payload, iso_date, string_list


def source_row(**overrides):
    row = {
        "id": 9, "caseid": "（2026）测字第😀号", "title": "  特殊“引号”与𠮷字案  ",
        "casecause": "合同纠纷", "casecausefull": '["合同纠纷", "合同纠纷"]',
        "casetype": "民事", "country": "中国", "court": "测试法院", "courtlevel": "基层",
        "doctype": "判决书", "disputefocus": "效力；履行", "judgedate": "2026年9月4日",
        "procedure": "一审", "applicable_law": '["民法典\\n第五百零九条"]', "caselevel": "普通",
        "fact": "第一行\r\n第二行😀", "source": "来源", "source_file": "/server/path/案例.json",
        "source_url": "https://example.test/a?q=中文", "keywords": "甲、乙，甲", "summary": "摘要",
        "prosecutorial_process": None, "adjudication_result": "结果", "reasoning": "理由",
        "guiding_significance": "意义",
    }
    row.update(overrides)
    return row


def test_payload_preserves_unicode_normalizes_newlines_and_has_stable_unique_id():
    payload = case_payload(source_row())
    assert payload["externalCaseId"] == "legal_llm:（2026）测字第😀号:9"
    assert payload["title"] == "特殊“引号”与𠮷字案"
    assert payload["fact"] == "第一行\n第二行😀"
    assert payload["sourceFile"] == "案例.json"
    assert payload["judgmentDate"] == "2026-09-04"
    assert payload["keywords"] == ["甲", "乙"]
    assert "【裁判理由】\n理由" in payload["content"]


def test_lists_and_dates_fail_closed_without_losing_plain_text():
    assert string_list("不是 JSON；仍需保留") == ["不是 JSON", "仍需保留"]
    assert iso_date("2026/09/04") == "2026-09-04"
    assert iso_date("不明日期") is None
    assert case_payload(source_row(source_url="file:///secret"))["sourceUrl"] is None
