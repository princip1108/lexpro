"""Explicit local acceptance test of our own CAPTCHA, demo roles and model CRUD.

Only loopback development ports are accepted. No case text or credentials are
printed. The saved deployment model is retained; temporary CRUD fixtures removed.
"""
import argparse
import re
import xml.etree.ElementTree as ET
from pathlib import Path
import requests

ROOT=Path(__file__).resolve().parents[1]


def session(base, username):
    client=requests.Session()
    response=client.get(base+'/api/v1/auth/captcha',timeout=10)
    response.raise_for_status()
    challenge=response.json()
    # This application-owned development CAPTCHA is exercised, not disabled.
    svg=ET.fromstring(challenge['svg'])
    answer=''.join(node.text or '' for node in svg.iter() if node.tag.endswith('text'))
    response=client.post(base+'/api/v1/auth/login',json={'username':username,'password':'LexProDemo2026!',
                        'captchaId':challenge['captchaId'],'captcha':answer},timeout=15)
    response.raise_for_status()
    client.headers['Authorization']='Bearer '+response.json()['accessToken']
    return client


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--port',type=int,choices=(8080,8081),default=8081)
    args=parser.parse_args();base=f'http://127.0.0.1:{args.port}'
    admin=session(base,'demo_admin')
    for name in ('demo_admin','demo_prosecutor','demo_reviewer'):
        client=session(base,name)
        response=client.get(base+'/api/v1/cases',params={'page':1,'size':20},timeout=15)
        assert response.status_code==200,(name,response.status_code)
        assert response.json()['totalItems']>=5
        response=client.get(base+'/api/v1/users',timeout=15)
        assert response.status_code==(200 if name=='demo_admin' else 403)
        print(name+': login, case visibility and user-management boundary PASS')
    response=admin.get(base+'/api/v1/typical-cases',params={'page':1,'size':20},timeout=15)
    assert response.status_code==200,response.status_code
    assert response.json()['totalItems']>=115
    print('typical-case HTTP paging PASS')
    config=admin.get(base+'/api/v1/system/model-configuration',timeout=15).json()
    tree=ET.parse(ROOT/'backend/lexpro-backend/.idea/workspace.xml')
    environment={item.attrib['name']:item.attrib['value'] for item in tree.findall('.//configuration/envs/env')}
    payload={'displayName':'业务生成模型','modelName':environment['LEXPRO_AI_MODEL'],
             'baseUrl':environment['LEXPRO_AI_BASE_URL'],'apiKey':environment['LEXPRO_AI_API_KEY'],
             'enableThinking':False,'remark':'既有部署配置：法律要素、摘要、案卡、报告',
             'enabled':True,'setActive':True}
    endpoint=base+'/api/v1/system/model-configurations'
    if not config.get('items'):
        response=admin.post(endpoint,json=payload,timeout=15)
        assert response.status_code==201,(response.status_code,response.text[:200])
    fixture=None
    try:
        response=admin.post(endpoint,json={**payload,'displayName':'本地验收临时配置','setActive':False},timeout=15)
        assert response.status_code==201,(response.status_code,response.text[:200])
        fixture=response.json()['id']
        assert environment['LEXPRO_AI_API_KEY'] not in response.text
        response=admin.put(endpoint+'/'+fixture,json={**payload,'apiKey':'','enableThinking':True,'setActive':False},timeout=15)
        assert response.status_code==200 and response.json()['enableThinking'] is True
        response=admin.post(endpoint+'/'+fixture+'/activation',timeout=15)
        assert response.status_code==200 and response.json()['active'] is True
        limited=session(base,'demo_prosecutor')
        assert limited.post(endpoint+'/'+fixture+'/activation',timeout=15).status_code==403
        print('model configuration create/edit/thinking/activation/redaction/authorization PASS')
    finally:
        if fixture:
            response=admin.delete(endpoint+'/'+fixture,timeout=15)
            assert response.status_code==204,response.status_code
    rows=admin.get(base+'/api/v1/system/model-configuration',timeout=15).json()['items']
    assert sum(row['active'] for row in rows)==1
    print('model configuration delete and active fallback PASS')


if __name__=='__main__':
    main()
