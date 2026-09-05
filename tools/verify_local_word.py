"""Fictional DOCX upload acceptance: Unicode, table cells, real embedded-image OCR."""
import io
import json
import sys
import time
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from verify_local_configuration import session

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend/ai-service'))
from tests.test_model_clients import word_fixture


def fixture(with_image):
    extras = {
        '[Content_Types].xml': '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>',
        '_rels/.rels': '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>',
    }
    body = '<w:p><w:r><w:t>😀𠮷【完全虚构测试】张某在北京接受询问。金额：12345.67元 &amp; “引号”。</w:t><w:tab/><w:t>制表符</w:t><w:br/><w:t>第二行</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>表格字段</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>张某</w:t></w:r></w:p></w:tc></w:tr></w:tbl>'
    if with_image:
        picture = Image.new('RGB', (1000, 200), 'white')
        ImageDraw.Draw(picture).text((40, 50), '虚构图片材料：李某在上海接受询问。',
                                    font=ImageFont.truetype('C:/Windows/Fonts/simhei.ttf', 32), fill='black')
        data = io.BytesIO(); picture.save(data, format='PNG')
        extras['word/media/test.png'] = data.getvalue()
        extras['word/_rels/document.xml.rels'] = '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="img" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/test.png"/></Relationships>'
        body += '<w:p><w:r><w:drawing><a:blip r:embed="img"/></w:drawing></w:r></w:p>'
    return word_fixture(body, extras)


def main():
    base = 'http://127.0.0.1:8080'
    client = session(base, 'demo_admin')
    response = client.get(base + '/api/v1/cases', params={'keyword': 'V7-LOCAL-FICTIONAL-001'}, timeout=15)
    response.raise_for_status()
    case = next(item for item in response.json()['items'] if item.get('caseNo') == 'V7-LOCAL-FICTIONAL-001')
    prefix = base + '/api/v1/cases/' + str(case['caseId'])
    for image in (False, True):
        filename = 'word-fictional-' + ('image' if image else 'text') + '.docx'
        response = client.post(prefix + '/dossier/files', files={'file': (
            filename, fixture(image), 'application/vnd.openxmlformats-officedocument.wordprocessingml.document')}, timeout=30)
        assert response.status_code == 201, response.status_code
        dossier = response.json()
        response = client.post(prefix + f"/dossier/files/{dossier['dossierId']}/parse-jobs", json={}, timeout=15)
        assert response.status_code == 202, response.status_code
        doc_id = response.json()['docId']
        deadline = time.monotonic() + 300
        while time.monotonic() < deadline:
            response = client.get(prefix + f'/documents/{doc_id}', timeout=15)
            assert response.status_code == 200, response.status_code
            result = response.json()
            if result['parseStatus'] == 'FAILED':
                raise RuntimeError(result.get('errorCode') or 'Word parse failed')
            if result['parseStatus'] == 'SUCCESS':
                text = result['rawText']
                assert text.startswith('😀𠮷') and '表格字段' in text and '12345.67元 & “引号”' in text
                assert '\t制表符\n第二行' in text
                if image:
                    assert '李某' in text and '上海' in text
                data = result['parsedText']
                if isinstance(data, str): data = json.loads(data)
                assert data['parser'] == 'docx'
                for block in data['blocks']:
                    assert text.encode('utf-16-le')[block['globalStartUtf16'] * 2:block['globalEndUtf16'] * 2].decode('utf-16-le') == block['text']
                    assert block['pageNo'] is None
                assert 'fileUrl' not in result and 'prompt' not in result
                print(f"DOCX {'image+text' if image else 'text+table'} upload201/parse202/read200 persisted UTF16 PASS; docId={doc_id}", flush=True)
                break
            time.sleep(2)
        else:
            raise RuntimeError('Word parse acceptance timed out')


if __name__ == '__main__':
    main()
