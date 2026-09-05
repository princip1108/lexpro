# LexPro Technical Decisions

[中文版](zh-CN/DECISIONS.md)

| ID | Status | Decision | Reason |
|---|---|---|---|
| ADR-001 | Accepted | Use a modular Spring Boot monolith. | Current team/project scale does not justify distributed services. |
| ADR-002 | Accepted | Use Java 21, Spring Boot 3.5.x, Maven and MyBatis-Plus. | Matches the selected backend stack and existing-schema workflow. |
| ADR-003 | Accepted | Organize backend code by business feature. | Keeps controllers, services, DTOs and persistence code for one capability together. |
| ADR-004 | Accepted | Use PostgreSQL schema `lexpro` with the 32-table V3 baseline. | This is the reviewed normalized final database. |
| ADR-005 | Accepted | Return response DTOs, never persistence entities. | Prevents schema leakage and sensitive-field exposure. |
| ADR-006 | Accepted | Use resource JSON for success and `ProblemDetail` for errors. | Preserves HTTP semantics and gives the frontend stable error codes. |
| ADR-007 | Accepted | No public registration; accounts are administered internally. | Fits the internal legal-system use case. |
| ADR-008 | Accepted | Use Spring Security, BCrypt and 30-minute HS256 JWT access tokens without refresh tokens. Logout discards the client token; account changes invalidate older tokens. | Provides a small stateless baseline without Redis or token tables; live account and permission checks limit stale authorization. |
| ADR-009 | Accepted | Store file binaries outside PostgreSQL behind a storage interface. | Supports local development and later MinIO without changing domain services. |
| ADR-010 | Accepted | Add a Python service only for document/AI processing and typical-case retrieval algorithms. | Keeps Java authoritative for business data while using the appropriate document, retrieval and AI ecosystem. |
| ADR-011 | Superseded | Defer vector dimension/index selection until M7. | Superseded by ADR-017 after the retrieval model evaluation. |
| ADR-012 | Accepted | Do not model announcements, schedules or notifications in the current backend. | They are prototype-only and not part of the confirmed core database. |
| ADR-013 | Accepted | Keep reviewed DBM scripts immutable; Flyway uses traceable derived copies and explicitly baselines the existing database at V3. | Prevents accidental replay while supporting reproducible fresh databases and forward-only V4+ migrations. |
| ADR-014 | Accepted | Integrate one controlled MCP Server as an adapter over approved application capabilities. It is not a new business-data authority. | Enables MCP clients without bypassing Java service-layer authorization, validation, auditing or sensitive-data controls. |
| ADR-015 | Accepted | Use the OpenAI-compatible endpoint at `https://api.deepseek.com` with configured model ID `deepseek-v4-flash` for the first M5 entity-recognition adapter. | Reuses a narrow provider interface while keeping credentials in the environment and external case-data transfer behind a separate disabled-by-default switch. |
| ADR-016 | Accepted | Use ordered `sections` JSON for report templates/content, the approved template/report lifecycles, and one generic DOCX/PDF judicial-document layout. PDF uses a configured local TTF font. | Keeps generation, editing and export on one validated contract without adding template engines or report-revision tables. |
| ADR-017 | Accepted | Use local `BAAI/bge-m3` embeddings with 1024 dimensions, cosine distance and a pgvector HNSW index. Python receives a restricted read-only connection to the typical-case corpus; Java remains the only authoritative writer. | Supports Chinese legal retrieval without exporting case text to an external model and preserves the Java authorization/audit boundary. |
| ADR-018 | Superseded | Implement one in-process Java MCP Server using stateless Streamable HTTP at `/mcp`. Reuse the existing Bearer JWT on every request and expose only the approved read-only tool allowlist. | Superseded because the MCP Server must now support external project-to-project calls rather than interactive LexPro users. |
| ADR-019 | Accepted | Keep one in-process Java MCP Server at `/mcp`, replace web-user JWT authentication with per-client MCP service tokens, and expose three DeepSeek-backed legal-processing tools plus one read-only partner typical-case recommendation tool. The recommendation tool reuses the application service without impersonating a web user or writing recommendation history. | Provides a deployable server-to-server identity, rotation, revocation, scoped authorization and audit without requiring external projects to simulate user login, while keeping the Java service and partner-provider controls authoritative. |
| ADR-020 | Accepted | Real case text may be sent to DeepSeek by the three approved AI-processing tools when `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true`; least-data, authorization, audit and log-redaction controls remain mandatory. | The project owner explicitly approved DeepSeek transfer on 2026-08-12 while retaining a disabled-by-default deployment control and the existing sensitive-data boundary. |
| ADR-021 | Accepted | Typical-case recommendation selects exactly one `LOCAL` or `PARTNER` provider per deployment. `PARTNER` calls the partner-owned Qwen/DELTA/MLP service through a separately managed loopback SSH tunnel, uses short-lived signed analysis tokens, and mirrors validated results into LexPro without storing partner vectors. No automatic provider fallback is allowed. | Preserves the partner's model and corpus ownership while keeping LexPro authorization, field normalization, history, favorites, reports, transactions and audit authoritative. Explicit provider and data-export switches prevent silent transfer or behavior changes. |

## Local model routing update

The owner requested LexPro for all language-model processing. Local IDEA program arguments and the acceptance launcher now route legal elements, summaries, case cards and reports to `LexPro_8B` at `http://127.0.0.1:8001/v1`; entity recognition retains its internal LexPro adapter. MinerU remains the document/image parser. The local deployment disables dynamic model-registry overrides without deleting saved configurations, and does not fall back to DeepSeek. This supersedes the earlier DeepSeek routing for this local deployment only; typical-case retrieval is a separate service and is unchanged. The 4096-token server context limit and generation quality still require acceptance.

## Pending decisions

- Production MinIO layout, retention/permanent-purge policy and malware-scanning requirements; M4 currently uses configurable local development storage.
- Sensitive identity-field encryption/masking mechanism.
- Case status-transition matrix.
- AI evaluation/data-classification rules beyond the approved DeepSeek transfer, and final production timeout/concurrency limits.
- First production client IDs/scopes/quotas and named MCP client acceptance.
- Production reverse proxy and source-network policy.
