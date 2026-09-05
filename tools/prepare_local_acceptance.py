"""Non-destructive local acceptance data setup; defaults to a read-only preview.

The corpus metadata is imported without embeddings: recommendation still uses the
configured server provider. Existing rows/passwords are never replaced. Requires
the existing retrieval venv, psycopg and bcrypt. --apply requires a verified dump.
"""
import argparse
import json
import os
import subprocess
from pathlib import Path

import bcrypt
import psycopg
from psycopg.types.json import Jsonb

from migrate_legal_llm import ROOT, DEFAULT_DB, connection, case_payload

DEMO_USERS = (
    ('demo_admin', '管理员', 'ADMIN'),
    ('demo_prosecutor', '检察官', 'DEMO_PROSECUTOR'),
    ('demo_reviewer', '审查人员', 'DEMO_REVIEWER'),
)
PERMISSIONS = ('DASHBOARD_VIEW', 'CASE_READ', 'CASE_WRITE', 'DOSSIER_MANAGE',
               'AI_EXECUTE', 'REPORT_MANAGE', 'RECOMMENDATION_USE', 'TASK_MANAGE')


def source_data(limit):
    with connection(DEFAULT_DB) as source:
        # Round-robin case types so a small pilot covers more than one filter.
        rows = source.execute('''SELECT * FROM case_base ORDER BY
            row_number() OVER (PARTITION BY casetype ORDER BY id), casetype, id LIMIT ?''', (limit,)).fetchall()
        cases = [dict(row) for row in source.execute('SELECT id, case_no, prosecutor_id FROM cases ORDER BY id')]
    return [case_payload(row) for row in rows], cases


def import_metadata(cursor, payloads):
    inserted = 0
    for p in payloads:
        cursor.execute('''INSERT INTO lexpro.typical_case
            (external_case_id,title,case_cause,case_cause_full_json,case_type,country,court,court_level,
             doc_type,dispute_focus_json,judgment_date,procedure,applicable_law_json,case_level,
             source_name,source_file,source_url,keywords_json)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            ON CONFLICT (external_case_id) WHERE external_case_id IS NOT NULL DO NOTHING
            RETURNING typical_case_id''',
            (p['externalCaseId'], p['title'], p['caseCause'], Jsonb(p['caseCauses']), p['caseType'],
             p['country'], p['court'], p['courtLevel'], p['docType'], Jsonb(p['disputeFocus']),
             p['judgmentDate'], p['procedure'], Jsonb(p['applicableLaws']), p['caseLevel'],
             p['sourceName'], p['sourceFile'], p['sourceUrl'], Jsonb(p['keywords'])))
        created = cursor.fetchone()
        if not created:
            continue
        cursor.execute('''INSERT INTO lexpro.typical_case_content
            (typical_case_id,content,fact,summary,prosecutorial_process,adjudication_result,reasoning,guiding_significance)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s)''',
            (created[0], p['content'], p['fact'], p['summary'], p['prosecutorialProcess'],
             p['adjudicationResult'], p['reasoning'], p['guidingSignificance']))
        inserted += 1
    return inserted


def setup_users(cursor, cases, password):
    cursor.execute("SELECT user_id, organization_id FROM lexpro.app_user WHERE username='admin' AND status='ACTIVE'")
    admin = cursor.fetchone()
    if not admin:
        raise ValueError('An active local admin account is required')
    users = {}
    for role_code, name in [('ADMIN', '管理员'), ('DEMO_PROSECUTOR', '检察官'), ('DEMO_REVIEWER', '审查人员')]:
        cursor.execute('''INSERT INTO lexpro.auth_role (role_code,role_name,description)
            VALUES (%s,%s,'Local acceptance demo role') ON CONFLICT (role_code) DO NOTHING''', (role_code, name))
        cursor.execute('SELECT role_id FROM lexpro.auth_role WHERE role_code=%s', (role_code,))
        role_id = cursor.fetchone()[0]
        if role_code != 'ADMIN':
            cursor.execute('''INSERT INTO lexpro.auth_role_permission (role_id,permission_id)
                SELECT %s,permission_id FROM lexpro.auth_permission WHERE permission_code=ANY(%s)
                ON CONFLICT DO NOTHING''', (role_id, list(PERMISSIONS)))
        for username, real_name, user_role in DEMO_USERS:
            if user_role != role_code:
                continue
            cursor.execute('SELECT user_id,role_id,password_hash FROM lexpro.app_user WHERE lower(username)=lower(%s)', (username,))
            existing = cursor.fetchone()
            if existing:
                if existing[1] != role_id or not bcrypt.checkpw(password.encode(), existing[2].encode()):
                    raise ValueError('Existing demo-named account differs; refusing to replace its role/password')
                users[username] = existing[0]
                continue
            encoded = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=12)).decode()
            cursor.execute('''INSERT INTO lexpro.app_user (username,password_hash,real_name,role_id,organization_id)
                VALUES (%s,%s,%s,%s,%s) RETURNING user_id''', (username, encoded, real_name, role_id, admin[1]))
            users[username] = cursor.fetchone()[0]
    assignments = 0
    for source_case in cases:
        cursor.execute("SELECT case_id FROM lexpro.case_record WHERE case_no=%s AND case_source='legal_llm demo migration'", (source_case['case_no'],))
        matches = cursor.fetchall()
        if len(matches) != 1:
            raise ValueError('Expected exactly one previously migrated demo case per source case number')
        case_id = matches[0][0]
        for user_id, role, access in [(admin[0], 'COLLABORATOR', 'MANAGE'),
                                      (users['demo_admin'], 'COLLABORATOR', 'MANAGE'),
                                      (users['demo_prosecutor'], 'PROSECUTOR', 'EDIT'),
                                      (users['demo_reviewer'], 'REVIEWER', 'MANAGE')]:
            cursor.execute('''INSERT INTO lexpro.case_assignment (case_id,user_id,assignment_role,access_level,assigned_by)
                VALUES (%s,%s,%s,%s,%s) ON CONFLICT (case_id,user_id,assignment_role) WHERE ended_at IS NULL DO NOTHING''',
                (case_id,user_id,role,access,admin[0]))
            assignments += cursor.rowcount
    return users, assignments, admin[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--backup', type=Path)
    parser.add_argument('--limit', type=int, default=100)
    args = parser.parse_args()
    if not 1 <= args.limit <= 200:
        raise ValueError('Acceptance pilot must contain 1-200 cases')
    payloads, cases = source_data(args.limit)
    if not args.apply:
        print(json.dumps({'mode':'preview','typicalCases':len(payloads),'demoCases':len(cases),
                          'usernames':[user[0] for user in DEMO_USERS]}, ensure_ascii=False))
        return
    backup = args.backup.resolve() if args.backup else None
    if not backup or not backup.is_relative_to((ROOT/'backups').resolve()) or not backup.is_file():
        raise ValueError('Provide a verified local backups/*.dump file')
    subprocess.run([r'D:\SQL\PosrgreSQL\18\bin\pg_restore.exe','--list',str(backup)], check=True, capture_output=True)
    password = os.environ.get('LEXPRO_DEMO_PASSWORD', '')
    if not (12 <= len(password.encode()) <= 72 and any(c.isupper() for c in password)
            and any(c.islower() for c in password) and any(c.isdigit() for c in password)
            and any(not c.isalnum() for c in password)):
        raise ValueError('LEXPRO_DEMO_PASSWORD must satisfy the application password policy')
    # Fixed loopback development database; this script cannot target a remote server.
    with psycopg.connect(host='127.0.0.1', port=5432, user='postgres', dbname='lexpro') as target:
        with target.cursor() as cursor:
            cursor.execute('SET LOCAL lock_timeout = \'5s\'')
            inserted = import_metadata(cursor, payloads)
            users, assignments, admin_id = setup_users(cursor, cases, password)
            cursor.execute('''INSERT INTO lexpro.operation_log (user_id,operation_type,object_type,operation_result,detail)
                VALUES (%s,'TYPICAL_CASES_IMPORTED','TYPICAL_CASE','SUCCESS',%s)''',
                (admin_id, Jsonb({'count':inserted,'source':'legal_llm','mode':'metadata-only'})))
            cursor.execute('''INSERT INTO lexpro.operation_log (user_id,operation_type,object_type,operation_result,detail)
                VALUES (%s,'USER_BOOTSTRAPPED','USER','SUCCESS',%s)''',
                (admin_id, Jsonb({'count':len(users),'assignmentCount':assignments,'mode':'local-demo'})))
            cursor.execute('SELECT count(*) FROM lexpro.typical_case WHERE external_case_id=ANY(%s)',
                           ([p['externalCaseId'] for p in payloads],))
            if cursor.fetchone()[0] != len(payloads):
                raise ValueError('Post-import count validation failed')
    print(json.dumps({'insertedTypicalCases':inserted,'demoUsers':users,'newAssignments':assignments,
                      'embeddingsGenerated':False,'backup':str(backup)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
