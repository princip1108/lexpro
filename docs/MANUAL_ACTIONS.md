# LexPro Manual Actions and Acceptance

[中文版](zh-CN/MANUAL_ACTIONS.md)

These actions require a real environment, a business decision or explicit approval. Use fictional or masked data for all acceptance work. Never record passwords, JWTs, API keys or real case content in documentation, screenshots, Git or chat.

## Current state

- On 2026-08-01, PostgreSQL `5432`, Spring Boot `8080` and Vue `5173` passed the local startup gate; retrieval `8010` remained disabled.
- The development database is at the V3 baseline with 32 business tables. Never rerun V1/V2/V3.
- The administrator core flow, M9 workspace, non-destructive M4 file flow and local M5 text parsing have passed real-database HTTP/UI acceptance with fictional data.
- M9 uses V3 `knowledge_content` and `work_task`; only M7 vector retrieval requires V4.
- Review the uncommitted M3-M9 work and create a Git rollback commit before any migration or deployment action.

## Acceptance progress (2026-08-01)

- **PASS:** health/database/OpenAPI, authentication rejection and CORS, administrator case/knowledge/task CRUD, dashboard consistency, task subject validation, dossier catalog/upload/update/download, and local UTF-8 parsing.
- **PARTIAL:** M4 soft delete/restore and audit-log inspection remain manual; M6 has only passed draft-template creation and external-data guard checks.
- **BLOCKED:** `USER_A`/`USER_B` authorization checks need two prepared standard accounts. Positive M5/M6 AI flows require explicit external-data approval. M7 migration/retrieval and M8 real-client acceptance remain separately gated.
- The acceptance run found and fixed literal XML entities in non-script MyBatis workspace SQL; dashboard and task create/detail were rechecked against PostgreSQL after restart.

## Before acceptance

1. Configure IntelliJ `LexproBackendApplication` with `LEXPRO_DB_PASSWORD`, a unique 32-byte-or-longer `LEXPRO_JWT_SECRET`, and `LEXPRO_REPORT_PDF_FONT_PATH=C:\Windows\Fonts\simhei.ttf`.
2. Keep `LEXPRO_FLYWAY_ENABLED`, `LEXPRO_FLYWAY_BASELINE_ON_MIGRATE`, `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA`, `LEXPRO_RETRIEVAL_ENABLED` and `LEXPRO_MCP_ENABLED` set to `false` initially.
3. Prepare an administrator, assigned standard user `USER_A`, and unassigned standard user `USER_B`. Bootstrap the first administrator only if `app_user` is empty, then disable bootstrap and restart.
4. Review `git status` and `git diff` for secrets and generated/private files, then create an acceptance rollback commit.
5. Before V4 or deployment, create a custom-format PostgreSQL backup with `D:\SQL\PosrgreSQL\18\bin\pg_dump.exe` under ignored directory `D:\desktop\soph\lexpro\backups`.

## Startup gate

1. Start PostgreSQL service `postgresql-x64-18`.
2. Start `LexproBackendApplication` from IntelliJ.
3. Verify `GET /api/health`, `GET /api/health/database` and Swagger UI. Database health must report `lexpro` and 32 business tables.
4. Start Vue with `D:\npm-global\node\npm.cmd run dev` and open `http://127.0.0.1:5173`.
5. Keep Python retrieval, MCP and external AI disabled until the core flow passes.

Stop acceptance if any health gate fails.

## Acceptance sequence

### A. Auth, users, cases and M9 workspace

1. Verify bad login returns `401`; successful login returns user/permissions without password hashes.
2. Create/disable/enable `USER_A` and `USER_B` and verify disabled login is rejected.
3. Create a fictional case, assign `USER_A`, and leave `USER_B` unassigned. `USER_A` can read it; direct access by `USER_B` returns `404` without case data.
4. Create draft knowledge as administrator. The administrator sees it; a non-`CONTENT_MANAGE` user cannot.
5. Create one case-linked and one knowledge-linked task. Both start `PENDING` and are assigned to their creator.
6. Submit a task containing both `caseId` and `contentId`; expect `400 WORK_TASK_SUBJECT_INVALID`.
7. Check dashboard case/task metrics, categories, recent cases and urgent tasks against the lists.
8. Confirm knowledge/task create and update audit entries in `operation_log`.

Do not directly update database states for acceptance. Case, knowledge and task transitions remain out of acceptance until their matrices are approved. The positive non-manager `PUBLISHED` knowledge scenario requires existing published content or the approved transition API.

### B. M4 dossier

Follow [M4 dossier management](M4_DOSSIER_MANAGEMENT.md): create folders/tags, upload a fictional UTF-8 `.txt`, list/rename/move/download/soft-delete/restore it, verify internal storage paths are absent, and verify `USER_B` receives `404`.

### C. M5 document and AI processing

Follow [M5 processing](M5_DOCUMENT_PROCESSING.md): parse the UTF-8 text with AI disabled first. For fictional data only, temporarily enable `LEXPRO_AI_ENABLED` and `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA`, restart, and accept entity recognition, legal elements, summaries, source quotes and first confirmation. Disable external transfer afterward. PDF/Office parsing is not a current pass condition.

### D. M6 case cards and reports

Follow [M6 case cards and reports](M6_CASE_CARDS_AND_REPORTS.md): create/activate templates, generate and confirm a case card, generate/edit/submit/return/finalize a report, export DOCX/PDF, and verify finalized reports are immutable.

### E. M7 retrieval, after separate V4 approval

1. Check `lexpro.flyway_schema_history` for V4.
2. If absent, back up first and explicitly approve `V4__configure_typical_case_vectors.sql`; never rerun V1/V2/V3.
3. Create the read-only retrieval account and configure `LEXPRO_RETRIEVAL_DATABASE_URL`.
4. Start `backend/retrieval-service`, verify `http://127.0.0.1:8010/health`, enable Java retrieval and restart Java.
5. Import fictional typical cases and verify idempotency, hybrid retrieval, reasons, history, favorites and controlled failure/degradation.

M7 does not block A-D or M9 acceptance.

### F. M8 real MCP client

Choose a client that supports Streamable HTTP and Bearer headers. Create a fictional test client in a local hashed MCP registry, set `LEXPRO_MCP_CLIENT_REGISTRY_PATH`, temporarily enable MCP, and connect to `http://127.0.0.1:8080/mcp` with that client's service token. Verify the four-tool catalog, one mocked/fictional call for each DeepSeek-backed tool, the fixed typical-case placeholder error, bounded output, permission denial and `MCP_TOOL_CALLED` audit. Disable MCP afterward. Production exposure must use HTTPS and a separate production registry.

## Acceptance evidence

For each phase record only the account/role, path, HTTP status, stable `errorCode`, `X-Request-Id`, one necessary fictional-data screenshot/export, corresponding audit operation/time, and `PASS`, `FAIL` or `BLOCKED`. Do not create a duplicate test report.

Any `500`, sensitive-data leak, unauthorized read, missing audit or database-constraint error fails the phase and blocks dependent acceptance.

## Decisions still required

Feature completion:

- Case transition matrix, rollback/reopen behavior and authorized roles.
- Knowledge-content and work-task transition matrices and authorized roles.
- Identity-number encryption, key source, hash/search, masking and update policy.
- Final case-number generation and uniqueness policy.

Before production:

- Object storage/MinIO layout, retention/permanent deletion and malware scanning.
- Production PDF/Office/MinerU provider, timeouts and concurrency.
- Real case-data transfer to DeepSeek is approved when `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true`; production AI limits/fallback remain to be finalized.
- Named MCP client, TLS/network exposure and first production client scopes/quotas.
- Deployment target, domain/TLS, log retention, monitoring and secret management.

## Pre-release validation

1. Establish latency/error baselines for login, case paging, dashboard, file listing, knowledge and task listing in an isolated environment.
2. Restore the latest backup with `pg_restore.exe` into a disposable database and verify 32 business tables, Flyway version and key record counts. Never overwrite development data.
3. Verify deployment TLS, least-privilege database identity, directory permissions, secret injection and log redaction.
4. Repeat health checks and phase A smoke tests in the deployment environment.
5. Review Git diff, build artifacts and acceptance evidence before separately approving deployment or release.
