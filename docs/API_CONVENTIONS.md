# LexPro API Conventions

[中文版](zh-CN/API_CONVENTIONS.md)

## General

- Base path: `/api/v1`.
- JSON field names: `camelCase`.
- Database names remain `snake_case` and are not exposed as an API convention.
- Content type: `application/json; charset=UTF-8`, except file upload/download.
- Timestamps: ISO-8601 with an offset, for example `2026-07-28T10:30:00+08:00`.
- Dates: ISO `yyyy-MM-dd`.

## HTTP semantics

| Operation | Status |
|---|---:|
| Successful query/update | `200 OK` |
| Resource created | `201 Created` |
| Async work accepted | `202 Accepted` |
| Successful operation with no body | `204 No Content` |
| Invalid input | `400 Bad Request` |
| Missing/invalid authentication | `401 Unauthorized` |
| Authenticated but forbidden | `403 Forbidden` |
| Resource not found | `404 Not Found` |
| State/version conflict | `409 Conflict` |
| Unsupported file/media | `415 Unsupported Media Type` |
| Unexpected failure | `500 Internal Server Error` |

Successful responses return the resource or operation result directly. Do not wrap every response in `{code, message, data}`.

## Request correlation

- Every HTTP response includes `X-Request-Id`.
- Clients may provide `X-Request-Id` using 8 to 64 ASCII letters, digits, dots, underscores, or hyphens.
- Missing or unsafe values are replaced by a server-generated UUID.
- The same value appears in request completion logs, error responses, and audit records created during the request.

## Errors

Errors use Spring `ProblemDetail` (`application/problem+json`) with stable extensions:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid",
  "instance": "/api/v1/cases",
  "errorCode": "VALIDATION_FAILED",
  "requestId": "01J...",
  "fieldErrors": {
    "caseName": "must not be blank"
  }
}
```

`errorCode` is stable for frontend logic; `detail` is for display/diagnostics. Never expose stack traces, SQL, passwords, tokens or storage paths.

## Pagination

- Request: `page` starts at 1, `size` defaults to 20 and is limited to 100.
- Sorting must use an endpoint-specific allowlist; never pass raw field names into SQL.
- The shared Java contracts are `PageRequest` and `PageResponse<T>`.

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

## Authentication

- Protected APIs use `Authorization: Bearer <access-token>`.
- Public endpoints are limited to health checks, local API documentation and login.
- The backend is authoritative for permissions and case visibility.
- Access tokens use HS256, expire after 30 minutes by default and are not refreshable.
- `POST /api/v1/auth/logout` records the action; the client must discard the token. There is no server-side token blacklist.
- Protected requests reload the account status and role permissions. Disabled/deleted/changed accounts cannot continue using an older token.
- Login, current-user and token responses use `Cache-Control: no-store`.

### M2 endpoints

| Method | Path | Access | Result |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Public | Access token, expiry and current user |
| `GET` | `/api/v1/auth/me` | Authenticated | Current user, organization, role and permissions |
| `POST` | `/api/v1/auth/logout` | Authenticated | `204 No Content` |
| `GET` | `/api/v1/users` | `USER_MANAGE` | Paged users |
| `GET` | `/api/v1/users/{userId}` | `USER_MANAGE` | User detail |
| `POST` | `/api/v1/users` | `USER_MANAGE` | `201 Created` user |
| `PATCH` | `/api/v1/users/{userId}/status` | `USER_MANAGE` | Enabled/disabled user |
| `PUT` | `/api/v1/users/{userId}/password` | `USER_MANAGE` | `204 No Content` |
| `GET` | `/api/v1/organizations/tree` | `USER_MANAGE` | Organization tree |
| `GET` | `/api/v1/roles` | `USER_MANAGE` | Roles with permission codes |
| `GET` | `/api/v1/permissions` | `USER_MANAGE` | Permission catalog |

### M3 endpoints

| Method | Path | Access | Result |
|---|---|---|---|
| `GET` | `/api/v1/cases` | `CASE_READ` plus case visibility | Paged cases visible to the current user |
| `GET` | `/api/v1/cases/{caseId}` | `CASE_READ` plus case visibility | Case detail |
| `POST` | `/api/v1/cases` | `CASE_WRITE` | `201 Created` case with status `PENDING`; creator receives current `ASSIGNEE` + `MANAGE` assignment |
| `PUT` | `/api/v1/cases/{caseId}` | `CASE_WRITE` plus case `EDIT` access | Update case metadata; status is not changed |
| `GET` | `/api/v1/cases/{caseId}/parties` | `CASE_READ` plus case visibility | Parties without raw identity number or identity hash |
| `POST` | `/api/v1/cases/{caseId}/parties` | `CASE_WRITE` plus case `EDIT` access | `201 Created` party without raw identity-number input |
| `PUT` | `/api/v1/cases/{caseId}/parties/{partyId}` | `CASE_WRITE` plus case `EDIT` access | Update safe party fields |
| `GET` | `/api/v1/cases/{caseId}/assignments` | `CASE_READ` plus case visibility | Assignment history |
| `POST` | `/api/v1/cases/{caseId}/assignments` | `CASE_ASSIGN` plus case `MANAGE` access | `201 Created` current assignment for an active user |
| `POST` | `/api/v1/cases/{caseId}/assignments/{assignmentId}/end` | `CASE_ASSIGN` plus case `MANAGE` access | End a current assignment |

Case status transitions and raw identity-number capture are intentionally not exposed yet because their business/security policies are still open decisions.

### M4 endpoints

| Method | Path | Access | Result |
|---|---|---|---|
| `GET` | `/api/v1/cases/{caseId}/dossier/folders` | `CASE_READ` plus case visibility | Nested dossier folder tree |
| `POST` | `/api/v1/cases/{caseId}/dossier/folders` | `DOSSIER_MANAGE` plus case `EDIT` access | `201 Created` folder |
| `GET` | `/api/v1/cases/{caseId}/dossier/tags` | `CASE_READ` plus case visibility | Case-scoped file tags |
| `POST` | `/api/v1/cases/{caseId}/dossier/tags` | `DOSSIER_MANAGE` plus case `EDIT` access | `201 Created` tag |
| `GET` | `/api/v1/cases/{caseId}/dossier/files` | `CASE_READ` plus case visibility | File metadata; optional `folderId` and `includeDeleted` filters |
| `POST` | `/api/v1/cases/{caseId}/dossier/files` | `DOSSIER_MANAGE` plus case `EDIT` access | Multipart upload using `file`, optional `folderId`, and repeated `tagId` fields |
| `PUT` | `/api/v1/cases/{caseId}/dossier/files/{dossierId}` | `DOSSIER_MANAGE` plus case `EDIT` access | Rename, move and replace tags without changing the extension |
| `DELETE` | `/api/v1/cases/{caseId}/dossier/files/{dossierId}` | `DOSSIER_MANAGE` plus case `EDIT` access | `204 No Content`; soft deletion only |
| `POST` | `/api/v1/cases/{caseId}/dossier/files/{dossierId}/restore` | `DOSSIER_MANAGE` plus case `EDIT` access | Restore a soft-deleted file |
| `GET` | `/api/v1/cases/{caseId}/dossier/files/{dossierId}/content` | `CASE_READ` plus case visibility | Authenticated attachment download |

File metadata responses never contain `fileUrl`, an object key, or a local storage path. Download responses use `no-store`, attachment disposition, and `nosniff` headers.

### M5-S1 endpoints

| Method | Path | Access | Result |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/dossier/files/{dossierId}/parse-jobs` | `AI_EXECUTE` plus case `EDIT` access | `202 Accepted`; create a new current parse version and schedule after commit |
| `GET` | `/api/v1/cases/{caseId}/dossier/files/{dossierId}/parse-results` | `CASE_READ` plus case visibility | Parse-version history without extracted content |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}` | `CASE_READ` plus case visibility | One parse result with structured JSON and raw extracted text |

Starting the same file while its current job is fresh returns `409 PARSE_IN_PROGRESS`. Posting again after a failed result creates a new version. A `PROCESSING` job older than the configured stale threshold is first marked `FAILED` with `PROCESSING_INTERRUPTED`, then a retry version is created. Parsing is asynchronous and never returns internal object keys or paths.

### M5-S2 endpoints

| Method | Path | Access | Result |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/documents/{docId}/entity-recognition-jobs` | `AI_EXECUTE` plus case `EDIT` access | `202 Accepted`; schedule entity recognition after commit |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}/entity-recognition-jobs/{requestId}` | `CASE_READ` plus case visibility | Persistent audit-derived `PROCESSING`, `SUCCESS` or `FAILED` job state |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}/entity-results` | `CASE_READ` plus case visibility | Entity-result history for the parse result |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}/entity-results/{entityResultId}` | `CASE_READ` plus case visibility | Original and confirmed entities plus model provenance |
| `PUT` | `/api/v1/cases/{caseId}/documents/{docId}/entity-results/{entityResultId}/confirmation` | `AI_EXECUTE` plus case `EDIT` access | Store the first human-confirmed result without overwriting model output |

Starting recognition requires a successful parse result with extracted text. The job is asynchronous and its `requestId` is a server-generated UUID. Confirmation accepts an object containing an `entities` array and is immutable after the first successful confirmation. AI provider errors use stable codes and never expose provider response bodies, credentials or prompts. External transfer remains blocked with `503 AI_DATA_EXPORT_DISABLED` until the environment explicitly allows it.

### M5-S3 endpoints

| Method | Path | Access | Result |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/documents/{docId}/legal-element-jobs` | `AI_EXECUTE` plus case `EDIT` access | `202 Accepted`; schedule legal-element recognition after commit |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}/legal-element-jobs/{requestId}` | `CASE_READ` plus case visibility | Persistent audit-derived `PROCESSING`, `SUCCESS` or `FAILED` job state |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}/legal-element-results` | `CASE_READ` plus case visibility | Legal-element result history for the parse result |
| `GET` | `/api/v1/cases/{caseId}/documents/{docId}/legal-element-results/{elementResultId}` | `CASE_READ` plus case visibility | Original and confirmed elements, evidence and model provenance |
| `PUT` | `/api/v1/cases/{caseId}/documents/{docId}/legal-element-results/{elementResultId}/confirmation` | `AI_EXECUTE` plus case `EDIT` access | Store the first human-confirmed result without overwriting model output |

Every element must contain at least one exact quote from the parsed source. Optional `startOffset` and `endOffset` use Java UTF-16 indexes, with an inclusive start and exclusive end, and must select the same quote. Confirmation accepts an object containing a non-empty `elements` array and cannot overwrite an earlier confirmation.

### M5-S4 endpoints

| Method | Path | Access | Result |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/summary-jobs` | `AI_EXECUTE` plus case `EDIT` access | `202 Accepted`; generate a summary from one to twenty selected parse results |
| `GET` | `/api/v1/cases/{caseId}/summary-jobs/{requestId}` | `CASE_READ` plus case visibility | Persistent audit-derived `PROCESSING`, `SUCCESS` or `FAILED` job state |
| `GET` | `/api/v1/cases/{caseId}/summaries?summaryType=FULL` | `CASE_READ` plus case visibility | Version history for one summary type |
| `GET` | `/api/v1/cases/{caseId}/summaries/{summaryId}` | `CASE_READ` plus case visibility | One summary version and model provenance |
| `PUT` | `/api/v1/cases/{caseId}/summaries/{summaryId}/confirmation` | `AI_EXECUTE` plus case `EDIT` access | Mark one generated summary as human-confirmed |

Summary types are `FACT`, `PROCESS`, `CONCLUSION` and `FULL`. The start body contains `summaryType` and a unique `sourceDocIds` array. Every source must be a successful parse result in the same case. Versions are scoped by case and summary type; the prior current version remains current if generation fails. Confirmation accepts generated text as-is; edited text requires starting a new generation version.

### M6 case-card endpoints

| Method | Path | Access | Result |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/case-card-jobs` | `REPORT_MANAGE` + `AI_EXECUTE` plus case `EDIT` access | `202 Accepted`; generate a case card from typed sources |
| `GET` | `/api/v1/cases/{caseId}/case-card-jobs/{requestId}` | `CASE_READ` plus case visibility | Persistent case-card job state |
| `GET` | `/api/v1/cases/{caseId}/case-cards` | `CASE_READ` plus case visibility | Case-card history |
| `GET` | `/api/v1/cases/{caseId}/case-cards/{fillTaskId}` | `CASE_READ` plus case visibility | Task, typed sources and generated fields |
| `PUT` | `/api/v1/cases/{caseId}/case-cards/{fillTaskId}/fields/{fieldId}/confirmation` | `REPORT_MANAGE` plus case `EDIT` access | Confirm or reject one field once |
| `PUT` | `/api/v1/cases/{caseId}/case-cards/{fillTaskId}/confirmation` | `REPORT_MANAGE` plus case `EDIT` access | Confirm a draft after every field is resolved |

Source types are `DOCUMENT`, `ENTITY`, `LEGAL_ELEMENT` and `SUMMARY`. Generated fields retain an exact source quote, typed source identity, location, source dossier when available and confidence.

### M6 report endpoints

| Method | Path | Access | Result |
|---|---|---|---|
| `POST` | `/api/v1/report-templates` | `REPORT_MANAGE` | Create the next draft version for a template code |
| `GET` | `/api/v1/report-templates` | `REPORT_MANAGE` | Query template versions by optional type/status |
| `GET` | `/api/v1/report-templates/{templateId}` | `REPORT_MANAGE` | One template version |
| `PUT` | `/api/v1/report-templates/{templateId}/activation` | `REPORT_MANAGE` | Activate a draft and atomically disable the prior active version of the same code |
| `PUT` | `/api/v1/report-templates/{templateId}/disablement` | `REPORT_MANAGE` | Disable an active template version |
| `POST` | `/api/v1/cases/{caseId}/report-jobs` | `REPORT_MANAGE` + `AI_EXECUTE` plus case `EDIT` access | `202 Accepted`; generate a new version from an active template and explicit sources |
| `GET` | `/api/v1/cases/{caseId}/report-jobs/{requestId}` | `CASE_READ` plus case visibility | Persistent report job state |
| `GET` | `/api/v1/cases/{caseId}/reports` | `CASE_READ` plus case visibility | Report history, optionally filtered by type |
| `GET` | `/api/v1/cases/{caseId}/reports/{reportId}` | `CASE_READ` plus case visibility | Report content and normalized references |
| `PUT` | `/api/v1/cases/{caseId}/reports/{reportId}/draft` | `REPORT_MANAGE` plus case `EDIT` access | Edit title/content using `lockVersion` optimistic locking |
| `PUT` | `/api/v1/cases/{caseId}/reports/{reportId}/review-submission` | `REPORT_MANAGE` plus case `EDIT` access | Move the current `DRAFT` to `REVIEWING` using `lockVersion` |
| `PUT` | `/api/v1/cases/{caseId}/reports/{reportId}/review-return` | `REPORT_MANAGE` plus case `EDIT` access | Return the current `REVIEWING` report to `DRAFT`; requires a reason |
| `PUT` | `/api/v1/cases/{caseId}/reports/{reportId}/finalization` | `REPORT_MANAGE` plus case `MANAGE` access | Move the current `REVIEWING` report to immutable `FINAL` |
| `GET` | `/api/v1/cases/{caseId}/reports/{reportId}/exports/{format}` | `CASE_READ` plus case visibility | Download `DOCX` or `PDF`; statuses `DRAFT`, `REVIEWING`, `FINAL` only |

A generating version does not replace the prior current report until it succeeds. Template/report content uses an ordered `sections` contract; report section code, title and order must match the selected template. Transition requests include `lockVersion`. Downloads use attachment disposition, `no-store` and `nosniff`; non-final exports contain a draft mark.

### M7 typical-case recommendation endpoints

| Method | Path | Access | Result |
|---|---|---|---|
| `POST` | `/api/v1/typical-cases/imports` | `REPORT_MANAGE` + `AI_EXECUTE` | Normalize/embed and idempotently import up to 50 cases by `externalCaseId` |
| `GET` | `/api/v1/typical-cases` | `RECOMMENDATION_USE` | Paged corpus query; supports keyword, case cause/type and favorites-only filters |
| `GET` | `/api/v1/typical-cases/{typicalCaseId}` | `RECOMMENDATION_USE` | Typical-case metadata and content without its vector |
| `PUT` | `/api/v1/typical-cases/{typicalCaseId}/favorite` | `RECOMMENDATION_USE` | `204 No Content`; idempotently add favorite |
| `DELETE` | `/api/v1/typical-cases/{typicalCaseId}/favorite` | `RECOMMENDATION_USE` | `204 No Content`; idempotently remove favorite |
| `POST` | `/api/v1/cases/{caseId}/recommendations` | `RECOMMENDATION_USE` + `CASE_READ` plus case visibility | `201 Created`; run retrieval and persist one recommendation batch |
| `GET` | `/api/v1/cases/{caseId}/recommendations` | `RECOMMENDATION_USE` + `CASE_READ` plus case visibility | Recommendation history |
| `GET` | `/api/v1/cases/{caseId}/recommendations/{recommendId}` | `RECOMMENDATION_USE` + `CASE_READ` plus case visibility | Query snapshot, retrieval metadata and ranked results |

Recommendation input contains exactly one of `sourceSummaryId` or `factText`, optional `disputeFocus`, structured filters and a result limit from 1 to 50. Java authorizes the case before calling Python. A lexical-only degraded result is persisted with `degraded=true`; an unavailable Python/database service returns `503` and creates no recommendation batch.

### M8 MCP endpoint

- Endpoint: `POST /mcp`, stateless Streamable HTTP using MCP Java SDK `0.18.3`.
- Access: `Authorization: Bearer <service-token>` on every request. The token resolves through the external MCP client registry to a named `clientId` and scoped authorities; web-user JWTs are rejected.
- Enablement: disabled by default; set `LEXPRO_MCP_ENABLED=true` only in an approved environment.
- Inputs: each tool uses a closed JSON Schema (`additionalProperties=false`). Results are limited by `LEXPRO_MCP_MAX_ITEMS` and `LEXPRO_MCP_MAX_OUTPUT_CHARS`.
- Auditing: every tool call records `MCP_TOOL_CALLED` with the HTTP request ID; MCP input is never stored in the audit detail.

| MCP tool | Required authority | Result |
|---|---|---|
| `lexpro_recognize_legal_elements` | `AI_EXECUTE` | Validated legal elements with exact source evidence |
| `lexpro_recognize_entities` | `AI_EXECUTE` | Validated document entities with source offsets where available |
| `lexpro_summarize_case` | `AI_EXECUTE` | Bounded summary over explicitly supplied document text |
| `lexpro_push_typical_cases` | Registered client | Reserved placeholder; always returns `TYPICAL_CASE_PUSH_NOT_IMPLEMENTED` |

The catalog exposes no MCP resource, prompt, generic SQL, shell, filesystem or unrestricted HTTP capability. The first three tools use the approved DeepSeek-compatible clients and remain subject to the explicit external-case-data switch and input/output bounds.

### M9 workspace endpoints

| Method | Path | Access | Result |
|---|---|---|---|
| `GET` | `/api/v1/knowledge-contents` | Authenticated | Paged visible knowledge; only `PUBLISHED` for non-managers |
| `GET` | `/api/v1/knowledge-contents/statistics` | Authenticated | Statistics over the caller's visible knowledge scope |
| `GET` | `/api/v1/knowledge-contents/{contentId}` | Authenticated | Visible content detail including text or structured JSON |
| `POST` | `/api/v1/knowledge-contents` | `CONTENT_MANAGE` | `201 Created`; create a `DRAFT` content item |
| `PUT` | `/api/v1/knowledge-contents/{contentId}` | `CONTENT_MANAGE` | Edit type, title, body and owner organization without changing status |
| `GET` | `/api/v1/work-tasks` | `TASK_MANAGE` | Paged accessible tasks; defaults to tasks assigned to the caller |
| `GET` | `/api/v1/work-tasks/{taskId}` | `TASK_MANAGE` plus case visibility when linked | Accessible task detail |
| `POST` | `/api/v1/work-tasks` | `TASK_MANAGE` plus case `EDIT` when linked | `201 Created`; create a caller-assigned `PENDING` task |
| `PUT` | `/api/v1/work-tasks/{taskId}` | `TASK_MANAGE` plus case `EDIT` when linked | Edit subject and metadata without changing status or assignee |
| `GET` | `/api/v1/dashboard` | `DASHBOARD_VIEW` | Visible case metrics/categories/recent cases and caller task metrics/urgent tasks |

A knowledge item requires text or object/array JSON. A work task requires exactly one of `caseId` and `contentId`. M9 does not expose knowledge or task status commands until the transition matrices and authorized roles are approved. Announcements, schedules and notifications are excluded.

## Endpoint naming

- Use plural nouns: `/cases`, `/users`, `/files`.
- Use nested resources where the parent authorization boundary matters: `/cases/{caseId}/parties`.
- Use explicit command resources for business transitions: `/cases/{caseId}/status-transitions`.
- Do not encode verbs such as `getUserList` in resource paths.
