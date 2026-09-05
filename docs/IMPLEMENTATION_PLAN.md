# LexPro Implementation Plan

[中文版](zh-CN/IMPLEMENTATION_PLAN.md)

> Updated: 2026-09-03
> Delivery method: small AI-assisted vertical slices, each independently tested and accepted.

## Current baseline

- [x] Vue 3 frontend prototype and mock data.
- [x] PostgreSQL V1/V2/V3 design, 32 final business tables.
- [x] Spring Boot 3.5.16 project using Java 21.
- [x] PostgreSQL connection and database health endpoint.
- [x] MyBatis-Plus integration.
- [x] Read-only user list with password excluded.
- [x] Backend controller tests and application context test.
- [x] JWT authentication, live account/permission checks and BCrypt credentials.
- [x] Administrator user management and organization/RBAC queries.
- [x] Frontend login, session expiry handling and route protection.

## M0 - Repository and project governance

- [x] M0-S1 Initialize one Git repository at the LexPro root.
- [x] M0-S2 Add root ignore rules and project `AGENTS.md`.
- [x] M0-S3 Add implementation, API, business, database and decision documents.
- [x] M0-S4 Document backend startup and required environment variables.
- [x] M0-S5 Introduce Flyway without rerunning the existing V1/V2/V3 database.
  - [x] Add Flyway dependencies and safe-by-default configuration.
  - [x] Package traceable V1/V2/V3 resources without manual transaction wrappers.
  - [x] Add migration resource integrity tests.
  - [x] Execute V1/V2/V3 on a disposable database and verify 32 business tables.
  - [x] Back up, validate and explicitly baseline the existing development database at V3.

## M1 - Backend conventions

- [x] M1-S1 Standard `ProblemDetail` error responses and global exception handling.
- [x] M1-S2 Bean Validation for request DTOs and field error responses.
- [x] M1-S3 Shared pagination request/response contract.
- [x] M1-S4 SpringDoc OpenAPI and local Swagger UI.
- [x] M1-S5 Explicit Vue development CORS configuration.
- [x] M1-S6 Request ID, structured logging and audit helper.
- [x] M1-S7 Milestone code review and regression tests.

## M2 - Authentication, users and organizations

- [x] M2-S1 User detail DTO and lookup service.
- [x] M2-S2 Safe development administrator bootstrap.
- [x] M2-S3 BCrypt password handling and credential lookup.
- [x] M2-S4 Login API and JWT issuance.
- [x] M2-S5 Spring Security JWT authentication and authorization failures.
- [x] M2-S6 Current-user and logout APIs.
- [x] M2-S7 User create, disable and password-reset administration.
- [x] M2-S8 Organization tree, role and permission queries.
- [x] M2-S9 Replace frontend login mock and add route protection.
- [x] M2-S10 Authentication/security review.

## M3 - Case workflow

- [x] Case paging/filtering, create, detail and update.
- [ ] Explicit case status-transition rules.
  - Blocked until the transition matrix and authorized transition roles are confirmed.
- [x] Parties and safe sensitive identity-data handling.
  - Raw identity-number input, encryption, hashing and exact search remain blocked until the identity policy is confirmed.
- [x] Assignee, reviewer and collaborator assignment history.
- [x] Case-level authorization, deadline/overdue calculation and audit.
- [x] Replace todo-case and initial dashboard mocks.

## M4 - Dossier management

- [x] Storage abstraction and development local storage.
- [x] Secure upload, hash, metadata, list and download.
- [x] Folder tree, tags, soft deletion and case authorization.
- [ ] MinIO adapter only after storage requirements are confirmed.

## M5 - Document and intelligent processing

- [x] Parsing contract, async execution, retry and versioned parse results.
  - Local UTF-8 text parsing remains available. Real fictional-input acceptance passed for MinerU PDF parsing and image upload through Java to MinerU with persisted Chinese text. DOCX text/table extraction, embedded-image OCR through real MinerU, persistence and UTF-16 positioning passed real HTTP acceptance after tunnel restoration. Corrupt DOCX and legacy DOC return verified safe error codes; legacy DOC requires saving as DOCX/PDF. Broader corpus acceptance remains pending.
- [x] Entity recognition with original/final results and confirmation.
  - The internal LexPro_8B route now enforces the six approved entity classes and exact UTF-16 block/document locations. The legacy DeepSeek route remains separately gated by explicit external-data approval.
- [x] Legal-element recognition with evidence traceability.
  - Model output and first-write human confirmation require exact source quotes; optional offsets are validated against the parsed text.
- [x] Versioned case summaries and confirmation.
  - `FACT`, `PROCESS`, `CONCLUSION` and `FULL` summaries use explicit same-case parse sources; the current version changes only after successful generation.

## M6 - Case cards and reports

- [x] Case-card generation, typed sources, fields and confirmation.
- [x] Report templates, generation, editing, review and finalization.
  - [x] Versioned template creation/query, asynchronous report generation and optimistic draft editing.
  - [x] Approved template activation and report review/return/finalization transitions with audit.
- [x] Evidence/legal-element/typical-case references.
- [x] Editable Word and paginated PDF export using one generic judicial-document layout.

## M7 - Typical-case recommendation

- [x] Define the Java-to-Python internal retrieval contract and failure semantics.
- [x] Typical-case import, normalization, structured filtering and full-text recall.
- [x] Approve local `BAAI/bge-m3`, 1024 dimensions and cosine distance.
- [x] Add the forward-only V4 vector migration and cosine HNSW index.
- [x] Implement vector recall, RRF fusion, reranking and recommendation reasons in Python.
- [x] Enforce case authorization in Java, invoke Python, and persist recommendation history/favorites and audit events in Java.
- [x] Add configurable timeout, one bounded retry, lexical degradation, traceability and focused retrieval-quality tests.
- [x] Add an opt-in partner-hosted provider with signed two-stage analysis, strict field mapping, transactional local mirroring and no automatic fallback from the local provider.
- [x] Add and apply forward-only V6 metadata fields for the legal_llm corpus; expose the extended library filters without category counts.
- [x] Prepare the legal_llm SQLite corpus and demo-case migration path through application APIs.
  - Read-only preview accepts all 6,786 corpus rows with stable distinct IDs; five demo cases and four available dossier files were migrated locally.
  - Full corpus embedding/import is intentionally deferred to server collaboration; the development machine does not need a complete local BGE-M3 deployment. One referenced demo PDF remains unavailable locally.

## M8 - MCP integration

- [ ] Confirm the named MCP client(s) and complete real-client acceptance.
  - [x] Keep in-process Java, stateless Streamable HTTP at `/mcp` and Spring Boot as the trust boundary.
- [x] Replace the current web-user JWT dependency with dedicated external MCP service-token authentication and per-client audit identity.
- [x] Replace the legacy six-tool catalog with legal-element recognition, entity recognition, case summarization and read-only partner typical-case recommendation.
- [x] Reuse the three validated DeepSeek-compatible clients with strict input/output schemas and no raw prompt/provider response exposure.
- [x] Add bounded per-client concurrency/rate limits, token rotation/revocation, deployment-safe timeouts and focused security tests.
- [ ] Deploy behind HTTPS and complete protocol, authorization, isolation and real-client acceptance from an external project.

## M9 - Workspace and delivery

- [x] Local language-model routing unified on LexPro_8B with MinerU retained for parsing. Real fictional-input acceptance passed for entity recognition, legal elements, summaries, case cards and reports, including persistence. The Java generation transport uses HTTP/1.1 to avoid the vLLM h2c missing-body rejection; its focused local HTTP regression passed. Long-context and broader generation-quality acceptance remain pending.

- [ ] Original legal_llm UI discrepancy acceptance: typed legal-element rows, recommendation columns, case-card confirmation and model configuration are implemented; browser/server acceptance remains pending.
- [x] V7 model API configuration migration applied through Flyway after explicit approval and validated backup; 33 business tables, with V1-V6 unchanged.
- [x] Real local HTTP acceptance passed for model configuration CRUD/activation and role boundaries; fictional-input entity recognition, legal elements, summaries, case cards and reports completed and persisted. UTF-16 locations matched seven entity spans and three evidence quotes, including supplementary Unicode characters.
- [ ] Complete typical-case recommendation integration: the partner service on forwarded port 8000 remains unreachable; corpus browsing is verified separately. Original-UI visual acceptance remains pending.
- [x] Local acceptance corpus: 100 SQLite metadata samples imported without embeddings; five demo cases assigned to administrator, prosecutor and reviewer accounts. Existing users/passwords preserved.

- [x] Knowledge content and human work tasks.
  - Knowledge and task status-transition commands remain blocked until their transition matrices are approved.
- [x] Dashboard/statistics APIs.
- [x] Remove remaining in-scope frontend mocks.
- [ ] Security, performance, backup/restore and deployment validation.
  - [x] M9 access-boundary review, focused service tests, backend package and frontend production build.
  - [x] Administrator core flow, M9 workspace, non-destructive M4 flow and local M5 parsing accepted against the real development database.
  - [x] Multi-user role and case-level assignment grant/revoke accepted with a fictitious user against the real development database.
  - [x] Local HTTP smoke baseline: 40 sequential and 60 concurrent protected reads all returned 200; local p95 was 110.01 ms.
  - [ ] Destructive lifecycle checks, positive external-AI flows, production-scale performance, backup/restore rehearsal and deployment validation.

## Definition of done for one task

1. The accepted behavior and exclusions are written before implementation.
2. Code follows module boundaries and security rules.
3. Focused automated tests pass.
4. The affected application builds successfully.
5. The real API or UI behavior is manually verifiable.
6. Documentation, Chinese mirrors and this plan reflect the result.
7. Git diff contains only intentional changes.
