# LexPro Implementation Plan

[中文版](zh-CN/IMPLEMENTATION_PLAN.md)

> Updated: 2026-07-28  
> Delivery method: small AI-assisted vertical slices, each independently tested and accepted.

## Current baseline

- [x] Vue 3 frontend prototype and mock data.
- [x] PostgreSQL V1/V2/V3 design, 32 final tables.
- [x] Spring Boot 3.5.16 project using Java 21.
- [x] PostgreSQL connection and database health endpoint.
- [x] MyBatis-Plus integration.
- [x] Read-only user list with password excluded.
- [x] Backend controller tests and application context test.

## M0 - Repository and project governance

- [x] M0-S1 Initialize one Git repository at the LexPro root.
- [x] M0-S2 Add root ignore rules and project `AGENTS.md`.
- [x] M0-S3 Add implementation, API, business, database and decision documents.
- [x] M0-S4 Document backend startup and required environment variables.
- [ ] M0-S5 Introduce Flyway without rerunning the existing V1/V2/V3 database.

## M1 - Backend conventions

- [ ] M1-S1 Standard `ProblemDetail` error responses and global exception handling.
- [ ] M1-S2 Bean Validation for request DTOs and field error responses.
- [ ] M1-S3 Shared pagination request/response contract.
- [ ] M1-S4 SpringDoc OpenAPI and local Swagger UI.
- [ ] M1-S5 Explicit Vue development CORS configuration.
- [ ] M1-S6 Request ID, structured logging and audit helper.
- [ ] M1-S7 Milestone code review and regression tests.

## M2 - Authentication, users and organizations

- [ ] M2-S1 User detail DTO and lookup service.
- [ ] M2-S2 Safe development administrator bootstrap.
- [ ] M2-S3 BCrypt password handling and credential lookup.
- [ ] M2-S4 Login API and JWT issuance.
- [ ] M2-S5 Spring Security JWT authentication and authorization failures.
- [ ] M2-S6 Current-user and logout APIs.
- [ ] M2-S7 User create, disable and password-reset administration.
- [ ] M2-S8 Organization tree, role and permission queries.
- [ ] M2-S9 Replace frontend login mock and add route protection.
- [ ] M2-S10 Authentication/security review.

## M3 - Case workflow

- [ ] Case paging/filtering, create, detail and update.
- [ ] Explicit case status-transition rules.
- [ ] Parties and sensitive identity-data handling.
- [ ] Assignee, reviewer and collaborator assignment history.
- [ ] Case-level authorization, deadline/overdue calculation and audit.
- [ ] Replace todo-case and initial dashboard mocks.

## M4 - Dossier management

- [ ] Storage abstraction and development local storage.
- [ ] Secure upload, hash, metadata, list and download.
- [ ] Folder tree, tags, soft deletion and case authorization.
- [ ] MinIO adapter only after storage requirements are confirmed.

## M5 - Document and intelligent processing

- [ ] Parsing contract, async execution, retry and versioned parse results.
- [ ] Entity recognition with original/final results and confirmation.
- [ ] Legal-element recognition with evidence traceability.
- [ ] Versioned case summaries and confirmation.

## M6 - Case cards and reports

- [ ] Case-card generation, typed sources, fields and confirmation.
- [ ] Report templates, generation, editing, review and finalization.
- [ ] Evidence/legal-element/typical-case references.
- [ ] Word/PDF export.

## M7 - Typical-case recommendation

- [ ] Typical-case import and structured/full-text filtering.
- [ ] Approve embedding model, dimension and distance algorithm.
- [ ] Add vector migration and HNSW index.
- [ ] Hybrid retrieval, ranking, reasons, history and favorites.

## M8 - Workspace and delivery

- [ ] Knowledge content and human work tasks.
- [ ] Dashboard/statistics APIs.
- [ ] Remove remaining in-scope frontend mocks.
- [ ] Security, performance, backup/restore and deployment validation.

## Definition of done for one task

1. The accepted behavior and exclusions are written before implementation.
2. Code follows module boundaries and security rules.
3. Focused automated tests pass.
4. The affected application builds successfully.
5. The real API or UI behavior is manually verifiable.
6. Documentation, Chinese mirrors and this plan reflect the result.
7. Git diff contains only intentional changes.
