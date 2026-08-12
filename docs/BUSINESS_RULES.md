# LexPro Confirmed Business Rules

[中文版](zh-CN/BUSINESS_RULES.md)

This document contains rules already supported by the final database and prior project decisions. Items under "Open decisions" must be confirmed before implementation.

## Users, organizations and permissions

- The system is internal and has no public user registration.
- Administrators create accounts and reset passwords.
- Each user has one primary organization (`organization_id`) and one system role (`role_id`).
- Roles map to permissions through `auth_role_permission`.
- Disabled users cannot authenticate.
- Passwords are stored as BCrypt hashes and are never returned by APIs.
- New/reset passwords contain at least 12 characters, include uppercase, lowercase, number and special characters, and do not exceed BCrypt's 72-byte input limit.
- Administrators cannot disable their own current account.
- Username and organization-code uniqueness is case-insensitive.
- Account status, password or profile changes invalidate access tokens issued before the change.

## Cases

- `case_record` is the case master record.
- `case_party` is the authoritative source for suspects, victims, witnesses and other parties.
- A user's case participation and access level are represented by `case_assignment`.
- A current assignment is defined by `ended_at IS NULL`; there is no `is_current` flag.
- Collaborator sharing uses assignment role `COLLABORATOR` and `access_level`, not a separate ACL table.
- Cross-case links are forbidden by the composite foreign-key design in V2.
- Date-only age is not stored; person age is derived from `birth_date` when needed.

## Dossiers and intelligent results

- File binary content is stored outside PostgreSQL. `evidence_file.file_url` stores an internal object key/URI.
- Deletion is soft deletion where provided by the schema.
- Parse results are versioned; only one current parse result exists per file.
- AI-generated artifacts preserve model, prompt, parameters, request ID, duration and token information where supported.
- Original AI output is preserved. Human-confirmed content is stored separately with confirmer and confirmation time.
- Long-running parsing/model requests must not block frontend HTTP requests.
- Real case text may be sent to the configured DeepSeek provider for the three approved AI-processing capabilities. This requires the explicit `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true` deployment switch and remains subject to least-data, access-control, audit and log-redaction requirements.

## Case cards, reports and recommendations

- Case-card fields preserve typed sources, source text/location and confidence.
- Reports are versioned. Regeneration creates a new version and does not overwrite history.
- Template status follows `DRAFT -> ACTIVE -> DISABLED`; a draft cannot be disabled directly. Activating a version disables the prior active version with the same template code in the same transaction. Disabled versions cannot be reactivated.
- Automatic report generation follows `GENERATING -> DRAFT | FAILED`. Human transitions are `DRAFT -> REVIEWING`, `REVIEWING -> DRAFT`, and `REVIEWING -> FINAL`; a final report is immutable.
- Submit and return require `REPORT_MANAGE` plus case `EDIT`; finalization requires `REPORT_MANAGE` plus case `MANAGE`.
- Template and report content use an ordered `sections` JSON contract. Report section code, title and order must exactly match its template.
- Report evidence, legal elements and typical cases use normalized reference tables.
- DOCX/PDF export uses one generic judicial-document layout. Non-final documents are marked as drafts.
- Typical cases are separate from operational cases; recommendation records connect them.
- Embedding dimension and index are deferred until the model is approved.

## Typical-case retrieval boundary

- Java remains authoritative for authentication, case visibility, business data, recommendation persistence and audit.
- Python owns retrieval/model work: normalization, structured/full-text/vector recall, fusion, reranking and recommendation-reason calculation.
- Python may process only the minimum data scope authorized by Java. It cannot independently grant case access.
- Recommendation results preserve the query snapshot, model and index-related parameters, ranked scores and reasons using the existing recommendation tables and supported metadata fields.
- A recommendation failure must not weaken authorization or silently replace an audited result with untracked output.
- The approved embedding contract is local `BAAI/bge-m3`, 1024 dimensions and cosine distance. V4 fixes both existing vector columns and adds a cosine HNSW index without adding a business table.
- If local vector generation or vector recall fails, Python may return a clearly marked lexical-only result. A database/service failure returns an error and creates no recommendation batch.
- Typical-case imports are idempotent by nonblank `external_case_id`; normalization and embedding happen in Python, while Java performs all corpus writes and import audit.

## MCP access boundary

- The project integrates one MCP Server, but it must reuse approved application services and must not bypass Java service-layer or case-level authorization.
- MCP tools are read-only by default and exposed through an explicit allowlist with validated inputs and bounded outputs.
- Every mutating MCP tool requires separate approval, explicit authorization and an `operation_log` audit record.
- MCP responses must not expose password hashes, raw identity numbers, internal storage paths, tokens, secrets or unauthorized case data.
- MCP calls must resolve to an authenticated named identity: an application user for user-bound tools or a registered `clientId` for approved server-to-server tools. Anonymous access cannot inherit either identity or any privilege.

## Current exclusions

- Announcements, schedules and notifications remain outside backend scope and are not shown on the M9 dashboard.
- No separate task-event, report-revision, model-call, user-role, user-organization or case-ACL tables are planned.
- `work_task` represents human work items; `operation_log` provides audit/timeline events.

## Workspace

- Authenticated users without `CONTENT_MANAGE` can read only `PUBLISHED` knowledge content; content managers can read all states and create or edit content.
- New knowledge content starts in `DRAFT`. General edits do not change status.
- Work-task access requires `TASK_MANAGE`; case-linked tasks additionally follow current case visibility and require case `EDIT` access when created or edited.
- A work task links to exactly one case or knowledge-content item. New tasks start in `PENDING` and are assigned to their creator.
- Task and knowledge changes are audited in `operation_log`. No separate task-event table is used.

## Open decisions

- Exact allowed case-status transition matrix.
- Exact knowledge-content and work-task transition matrices and authorized roles.
- Case-number uniqueness and generation rules.
- Identity-number encryption, search and masking policy.
- Whether the provisional M4 upload allowlist and 25 MB development limit need adjustment, and the retention period.
- Production object-storage layout, MinIO deployment and malware-scanning requirements.
- MinerU provider, production AI timeout/concurrency limits and model fallback policy.
- AI evaluation thresholds and provider-specific data-classification/masking rules beyond the approved DeepSeek transfer.
- Final approval of the external MCP service-token registry, first client scopes/quotas and named-client acceptance.
- MCP production network exposure and source-network policy.
