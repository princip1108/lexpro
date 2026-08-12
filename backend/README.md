# LexPro Backend

[中文说明](README.zh-CN.md)

## Stack

- Java 21
- Spring Boot 3.5.16
- Maven
- MyBatis-Plus 3.5.17
- PostgreSQL
- Flyway
- SpringDoc OpenAPI

The Maven project is in `backend/lexpro-backend`.

## Required environment

The local DBeaver connection uses database `lexpro` on `127.0.0.1:5432`. Configure the following environment variable in the IntelliJ run configuration:

```text
LEXPRO_DB_PASSWORD=<your PostgreSQL password>
LEXPRO_JWT_SECRET=<a unique random value of at least 32 bytes>
```

Optional overrides:

```text
LEXPRO_DB_URL=jdbc:postgresql://127.0.0.1:5432/lexpro?currentSchema=lexpro
LEXPRO_DB_USERNAME=postgres
LEXPRO_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
LEXPRO_JWT_ISSUER=https://lexpro.local
LEXPRO_JWT_ACCESS_TOKEN_TTL=PT30M
LEXPRO_LOGIN_RATE_LIMIT_ENABLED=true
LEXPRO_LOGIN_USERNAME_MAX_ATTEMPTS=5
LEXPRO_LOGIN_ADDRESS_MAX_ATTEMPTS=100
LEXPRO_LOGIN_RATE_LIMIT_WINDOW=PT5M
LEXPRO_LOGIN_BLOCK_DURATION=PT15M
LEXPRO_LOGIN_MAX_TRACKED_ENTRIES=10000
LEXPRO_DOSSIER_LOCAL_ROOT=./storage
LEXPRO_DOSSIER_MAX_FILE_SIZE=25MB
LEXPRO_DOSSIER_MAX_REQUEST_SIZE=26MB
LEXPRO_PROCESSING_CORE_THREADS=2
LEXPRO_PROCESSING_MAX_THREADS=4
LEXPRO_PROCESSING_QUEUE_CAPACITY=50
LEXPRO_PROCESSING_MAX_EXTRACTED_CHARS=2000000
LEXPRO_PROCESSING_STALE_AFTER=PT30M
LEXPRO_AI_ENABLED=false
LEXPRO_AI_BASE_URL=https://api.deepseek.com
LEXPRO_AI_API_KEY=<your local API key>
LEXPRO_AI_MODEL=deepseek-v4-flash
LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=false
LEXPRO_AI_CONNECT_TIMEOUT=PT5S
LEXPRO_AI_READ_TIMEOUT=PT45S
LEXPRO_AI_MAX_INPUT_CHARS=60000
LEXPRO_AI_MAX_OUTPUT_TOKENS=4096
LEXPRO_REPORT_PDF_FONT_PATH=C:\Windows\Fonts\simhei.ttf
LEXPRO_RETRIEVAL_ENABLED=false
LEXPRO_RETRIEVAL_BASE_URL=http://127.0.0.1:8010
LEXPRO_RETRIEVAL_CONNECT_TIMEOUT=PT3S
LEXPRO_RETRIEVAL_READ_TIMEOUT=PT20S
LEXPRO_RETRIEVAL_RETRIES=1
LEXPRO_RETRIEVAL_MODEL_NAME=BAAI/bge-m3
LEXPRO_RETRIEVAL_MODEL_VERSION=main
LEXPRO_MCP_ENABLED=false
LEXPRO_MCP_ENDPOINT=/mcp
LEXPRO_MCP_REQUEST_TIMEOUT=PT55S
LEXPRO_MCP_MAX_ITEMS=20
LEXPRO_MCP_MAX_OUTPUT_CHARS=50000
LEXPRO_MCP_CLIENT_REGISTRY_PATH=<absolute path to hashed client registry>
LEXPRO_MCP_TOKEN_RELOAD_INTERVAL=PT30S
LEXPRO_MCP_MAX_CONCURRENT_REQUESTS=8
LEXPRO_MCP_MAX_CONCURRENT_PER_CLIENT=2
LEXPRO_MCP_RATE_LIMIT_PER_MINUTE=60
```

Do not put a real database password, JWT secret, administrator password or AI API key in `application.properties` or commit it to Git. Changing the JWT secret invalidates all issued access tokens. AI enablement and case-text export remain separate switches. DeepSeek transfer is approved, but each intended deployment must explicitly set `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true`; the default remains `false`.

Login rate limiting is enabled by default and is local to one backend process. Keep it enabled in production; a multi-instance deployment requires an approved shared limiter before traffic is distributed across instances.

MCP rate and concurrency limits are also local to one backend process. A multi-instance MCP deployment must enforce the aggregate client quota at the reverse proxy or API gateway. Keep the client registry outside the repository and container image; only its strict JSON format and environment-variable path are documented.

## First development administrator

The bootstrap is disabled by default and only creates an administrator when `app_user` is empty. For a first local run, temporarily add these variables to the IntelliJ run configuration:

```text
LEXPRO_BOOTSTRAP_ADMIN_ENABLED=true
LEXPRO_BOOTSTRAP_ADMIN_USERNAME=admin
LEXPRO_BOOTSTRAP_ADMIN_PASSWORD=<a strong local password>
LEXPRO_BOOTSTRAP_ADMIN_REAL_NAME=System Administrator
LEXPRO_BOOTSTRAP_ORGANIZATION_CODE=LEXPRO
LEXPRO_BOOTSTRAP_ORGANIZATION_NAME=LexPro
```

The password must have at least 12 characters with uppercase, lowercase, number and special characters. Start once, confirm the account was created, then set `LEXPRO_BOOTSTRAP_ADMIN_ENABLED=false`. Existing user data is never overwritten.

## Flyway safety

The existing development database was registered as Flyway baseline version 3 on 2026-07-28. Flyway remains disabled by default so a migration still requires an explicit decision:

```text
LEXPRO_FLYWAY_ENABLED=false
LEXPRO_FLYWAY_BASELINE_ON_MIGRATE=false
```

Keep `LEXPRO_FLYWAY_BASELINE_ON_MIGRATE=false`. Enable Flyway only for an approved validation or migration; new schema work starts at V4. The current database contains 32 business tables and the `lexpro.flyway_schema_history` infrastructure table.

## Run in IntelliJ IDEA

1. Open `backend/lexpro-backend` as the Maven project.
2. Select Java 21.
3. Open **Run -> Edit Configurations** and add `LEXPRO_DB_PASSWORD` and `LEXPRO_JWT_SECRET` under environment variables.
4. Run `com.lexpro.lexprobackend.LexproBackendApplication`.

## Command line

With Java 21 available in `JAVA_HOME`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

## Current endpoints

```http
GET /api/health
GET /api/health/database
POST /api/v1/auth/login
GET /api/v1/auth/me
POST /api/v1/auth/logout
GET /api/v1/users?page=1&size=20
GET /api/v1/users/{userId}
POST /api/v1/users
PATCH /api/v1/users/{userId}/status
PUT /api/v1/users/{userId}/password
GET /api/v1/organizations/tree
GET /api/v1/roles
GET /api/v1/permissions
GET /api/v1/cases?page=1&size=20
GET /api/v1/cases/{caseId}
POST /api/v1/cases
PUT /api/v1/cases/{caseId}
GET /api/v1/cases/{caseId}/parties
POST /api/v1/cases/{caseId}/parties
PUT /api/v1/cases/{caseId}/parties/{partyId}
GET /api/v1/cases/{caseId}/assignments
POST /api/v1/cases/{caseId}/assignments
POST /api/v1/cases/{caseId}/assignments/{assignmentId}/end
GET /api/v1/cases/{caseId}/dossier/folders
POST /api/v1/cases/{caseId}/dossier/folders
GET /api/v1/cases/{caseId}/dossier/tags
POST /api/v1/cases/{caseId}/dossier/tags
GET /api/v1/cases/{caseId}/dossier/files
POST /api/v1/cases/{caseId}/dossier/files
PUT /api/v1/cases/{caseId}/dossier/files/{dossierId}
DELETE /api/v1/cases/{caseId}/dossier/files/{dossierId}
POST /api/v1/cases/{caseId}/dossier/files/{dossierId}/restore
GET /api/v1/cases/{caseId}/dossier/files/{dossierId}/content
POST /api/v1/cases/{caseId}/dossier/files/{dossierId}/parse-jobs
GET /api/v1/cases/{caseId}/dossier/files/{dossierId}/parse-results
GET /api/v1/cases/{caseId}/documents/{docId}
POST /api/v1/cases/{caseId}/documents/{docId}/entity-recognition-jobs
GET /api/v1/cases/{caseId}/documents/{docId}/entity-recognition-jobs/{requestId}
GET /api/v1/cases/{caseId}/documents/{docId}/entity-results
GET /api/v1/cases/{caseId}/documents/{docId}/entity-results/{entityResultId}
PUT /api/v1/cases/{caseId}/documents/{docId}/entity-results/{entityResultId}/confirmation
```

Only health, Swagger/OpenAPI and login are public. User, organization, role and permission APIs require `USER_MANAGE`.
Case APIs require `CASE_READ`, `CASE_WRITE` or `CASE_ASSIGN` plus service-layer case access.
Dossier reads/downloads require `CASE_READ` plus case visibility. Dossier changes require `DOSSIER_MANAGE` plus case `EDIT` access.
Parse-result reads require `CASE_READ`; starting or retrying parsing requires `AI_EXECUTE` plus case `EDIT` access.
Entity-result reads require `CASE_READ`; generation and confirmation require `AI_EXECUTE` plus case `EDIT` access.

Access tokens expire after 30 minutes by default and have no refresh token. Logout records an audit event and the frontend discards the token; it does not maintain a server-side blacklist.

Local API documentation:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

## Common API foundation

- Errors use `application/problem+json` with stable `errorCode`, `requestId`, and optional `fieldErrors` properties.
- Pagination starts at page 1, defaults to 20 items, and is limited to 100 items.
- Every response includes `X-Request-Id`; a safe client-provided ID is preserved.
- Request completion logs include method, path, status, duration, and request ID.
- `AuditService` writes security-sensitive or state-changing events to the existing `operation_log` table.
- CORS is limited to the configured Vue development origins and `/api/**` routes.

## M3 case workflow limits

New cases start as `PENDING`; ordinary update requests do not change status. Status-transition APIs are deferred until the transition matrix is confirmed. Party APIs return only masked identity data and do not accept raw identity numbers until the encryption, hashing, masking and search policy is approved.

## M4 development storage

Dossier binaries are stored under `LEXPRO_DOSSIER_LOCAL_ROOT` (default `./storage`) using random internal object keys. API metadata never exposes the key or local path. The default limit is 25 MB per file; supported extensions, MIME types and limits are configurable. See `docs/M4_DOSSIER_MANAGEMENT.md` for the complete rules and acceptance steps.

## M5 document and intelligent processing

Parsing is versioned and asynchronous. The built-in development parser supports active UTF-8 `.txt` files; other types fail with a stable provider-not-configured code until an approved parser adapter is added. Reposting after completion/failure creates a new version. See `docs/M5_DOCUMENT_PROCESSING.md`.

Entity recognition uses an OpenAI-compatible DeepSeek adapter and is also asynchronous. Original model output and first human-confirmed output are stored separately. Automated tests use a local mock provider. Real external transfer is blocked unless `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true` is explicitly configured after approval.

Legal-element recognition uses the same bounded asynchronous AI transport and requires exact source quotes for evidence. Case summaries use explicitly selected successful parse results and keep version history by summary type; a failed generation never replaces the last current summary. Both workflows support first-write human confirmation and enforce case access in the service layer.

## M6 case cards and reports

M6 provides asynchronous case-card/report generation, versioned report templates, optimistic draft editing, review/finalization transitions, normalized references and DOCX/PDF export. PDF export requires `LEXPRO_REPORT_PDF_FONT_PATH` to point to a readable Chinese TTF. See `../docs/M6_CASE_CARDS_AND_REPORTS.md` for contracts, permissions and acceptance steps.

## M7 typical-case recommendation

M7 adds a local Python retrieval service, idempotent typical-case import, structured/lexical/vector hybrid retrieval, recommendation history and favorites. Java remains the authorization and write boundary. V4 and the Python read-only database account require explicit manual approval before use. See `../docs/M7_TYPICAL_CASE_RECOMMENDATION.md`.

## M8 MCP Server

M8 provides one in-process Java MCP Server at `POST /mcp` using stateless Streamable HTTP. It is disabled by default. When enabled, every request requires a dedicated per-client MCP service token from the hashed registry; web-user JWTs are not accepted at this endpoint. The allowlist contains legal-element recognition, entity recognition, case summarization and a non-operational typical-case placeholder. The first three tools reuse the validated DeepSeek-compatible clients and require `AI_EXECUTE`; the placeholder always returns `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED`. No generic SQL, shell, filesystem or file-content capability is exposed. Production acceptance still requires HTTPS deployment and a named external MCP client.

## Expected database check

`GET /api/health/database` should return database `lexpro` and business table count `32`. The endpoint deliberately excludes Flyway's `flyway_schema_history` infrastructure table.
