"""Exercise the real local backend using explicitly fictional acceptance material."""
import time
import argparse
from verify_local_configuration import session

BASE='http://127.0.0.1:8080'
TEXT='😀𠮷【虚构测试材料，不对应真实案件】\n2026年9月1日，犯罪嫌疑人张某在北京市朝阳区向李某出售海洛因，涉案金额12345.67元。北京市公安局以张某涉嫌贩卖毒品罪立案。张某案发后主动投案，如实供述，认罪认罚。'


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--generation-only',action='store_true')
    args=parser.parse_args()
    client=session(BASE,'demo_admin')
    def call(method,path,**kwargs):
        response=client.request(method,BASE+path,timeout=30,**kwargs)
        if not response.ok:raise RuntimeError(f'{method} {path}: HTTP {response.status_code}, '+response.json().get('errorCode','unknown'))
        return response.json()
    def wait(path,key='status'):
        deadline=time.monotonic()+300
        while time.monotonic()<deadline:
            result=call('GET',path)
            if result[key]=='SUCCESS':return result
            if result[key]=='FAILED':raise RuntimeError(result.get('errorCode') or 'JOB_FAILED')
            time.sleep(2)
        raise RuntimeError('Acceptance wait timed out; task may still be running')
    matches=call('GET','/api/v1/cases',params={'keyword':'V7-LOCAL-FICTIONAL-001','page':1,'size':20})['items']
    case=next((item for item in matches if item.get('caseNo')=='V7-LOCAL-FICTIONAL-001'),None)
    if not case:case=call('POST','/api/v1/cases',json={'caseNo':'V7-LOCAL-FICTIONAL-001','caseName':'本地V7联调验收（完全虚构）','caseType':'刑事','caseCause':'贩卖毒品罪'})
    prefix=f"/api/v1/cases/{case['caseId']}"
    files=call('GET',prefix+'/dossier/files')
    file=next((item for item in files if item['fileName']=='v7-fictional-unicode.txt'),None)
    if not file:file=call('POST',prefix+'/dossier/files',files={'file':('v7-fictional-unicode.txt',TEXT.encode('utf-8'),'text/plain')})
    parsed=call('POST',prefix+f"/dossier/files/{file['dossierId']}/parse-jobs",json={})
    doc_path=prefix+f"/documents/{parsed['docId']}"
    document=wait(doc_path,'parseStatus')
    assert document['rawText']==TEXT
    print('upload + parse + Unicode round-trip PASS',flush=True)
    jobs=[]
    for kind in ([] if args.generation_only else ['entity-recognition','legal-element']):
        response=client.post(BASE+doc_path+'/'+kind+'-jobs',json={},timeout=30)
        if response.status_code!=202:raise RuntimeError(f'{kind}: HTTP {response.status_code} '+response.json().get('errorCode',''))
        jobs.append((kind,doc_path+'/'+kind+'-jobs/'+response.json()['requestId']))
    errors=[]
    for kind,path in jobs:
        try:
            result=wait(path)
            print(kind+' backend → provider → persisted result PASS',flush=True)
        except Exception as error:
            errors.append(kind+': '+str(error));print(errors[-1],flush=True)
    generation_jobs=[('summary',{'summaryType':'FACT','sourceDocIds':[parsed['docId']]}),
                     ('case-card',{'fillMode':'AUTO','sources':[{'sourceType':'DOCUMENT','sourceId':parsed['docId']}]})]
    templates=call('GET','/api/v1/report-templates',params={'status':'ACTIVE'})
    if templates:
        template=templates[0]
        generation_jobs.append(('report',{'reportType':template['templateType'],'reportTitle':'虚构材料联调报告',
            'templateId':template['templateId'],'evidence':[{'dossierId':file['dossierId'],'sortNo':1}]}))
    for kind,payload in generation_jobs:
        try:
            created=call('POST',prefix+'/'+kind+'-jobs',json=payload)
            result=wait(prefix+'/'+kind+'-jobs/'+created['requestId'])
            print(kind+' real model generation + persistence PASS',flush=True)
        except Exception as error:
            errors.append(kind+': '+str(error));print(errors[-1],flush=True)
    if errors:raise RuntimeError('; '.join(errors))


if __name__=='__main__':main()
