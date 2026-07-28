# LexPro Confirmed Business Rules

This document contains rules already supported by the final database and prior project decisions. Items under "Open decisions" must be confirmed before implementation.

## Users, organizations and permissions

- The system is internal and has no public user registration.
- Administrators create accounts and reset passwords.
- Each user has one primary organization (`organization_id`) and one system role (`role_id`).
- Roles map to permissions through `auth_role_permission`.
- Disabled users cannot authenticate.
- Passwords are stored as BCrypt hashes and are never returned by APIs.

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

## Case cards, reports and recommendations

- Case-card fields preserve typed sources, source text/location and confidence.
- Reports are versioned. Regeneration creates a new version and does not overwrite history.
- Report evidence, legal elements and typical cases use normalized reference tables.
- Typical cases are separate from operational cases; recommendation records connect them.
- Embedding dimension and index are deferred until the model is approved.

## Current exclusions

- Announcements, schedules and notifications remain frontend prototype features.
- No separate task-event, report-revision, model-call, user-role, user-organization or case-ACL tables are planned.
- `work_task` represents human work items; `operation_log` provides audit/timeline events.

## Open decisions

- Exact allowed case-status transition matrix.
- Case-number uniqueness and generation rules.
- Identity-number encryption, search and masking policy.
- Upload type allowlist, maximum size and retention period.
- Development/production object-storage paths and MinIO deployment.
- MinerU and model providers, authentication, timeout and concurrency limits.
- Report template JSON contract and Word/PDF visual format.
- AI evaluation thresholds and whether external providers may receive real case data.

