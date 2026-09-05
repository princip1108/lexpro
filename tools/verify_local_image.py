"""Upload an in-memory fictional text image through Java to real MinerU."""
import io
import time
from PIL import Image,ImageDraw,ImageFont
from verify_local_configuration import session

base='http://127.0.0.1:8080'
client=session(base,'demo_admin')
cases=client.get(base+'/api/v1/cases',params={'keyword':'V7-LOCAL-FICTIONAL-001'},timeout=15).json()['items']
case=next(item for item in cases if item.get('caseNo')=='V7-LOCAL-FICTIONAL-001')
prefix=base+'/api/v1/cases/'+str(case['caseId'])
picture=Image.new('RGB',(1200,350),'white')
draw=ImageDraw.Draw(picture)
font=ImageFont.truetype('C:/Windows/Fonts/simhei.ttf',30)
draw.multiline_text((40,35),'完全虚构的图片解析验收材料\n2026年9月1日，张某在北京市朝阳区接受询问。\n涉案金额为12345.67元。\n本图片仅用于测试上传、文字解析与原文定位。',font=font,fill='black',spacing=22)
buffer=io.BytesIO();picture.save(buffer,format='PNG')
files=client.get(prefix+'/dossier/files',timeout=15).json()
file=next((item for item in files if item['fileName']=='v7-fictional-image.png'),None)
if not file:
    response=client.post(prefix+'/dossier/files',files={'file':('v7-fictional-image.png',buffer.getvalue(),'image/png')},timeout=30)
    response.raise_for_status();file=response.json()
response=client.post(prefix+f"/dossier/files/{file['dossierId']}/parse-jobs",json={},timeout=15)
response.raise_for_status();doc=response.json()
deadline=time.monotonic()+240
while time.monotonic()<deadline:
    response=client.get(prefix+f"/documents/{doc['docId']}",timeout=15);response.raise_for_status();result=response.json()
    if result['parseStatus']=='FAILED':raise RuntimeError(result.get('errorCode') or 'MinerU image parse failed')
    if result['parseStatus']=='SUCCESS':
        assert '张某' in result['rawText'] and '12345.67' in result['rawText']
        print('Image upload → Java → MinerU → persisted Chinese text PASS; chars:',len(result['rawText']),flush=True)
        break
    time.sleep(2)
else:raise RuntimeError('Image parsing acceptance timed out')
