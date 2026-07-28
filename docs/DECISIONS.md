# LexPro Technical Decisions

| ID | Status | Decision | Reason |
|---|---|---|---|
| ADR-001 | Accepted | Use a modular Spring Boot monolith. | Current team/project scale does not justify distributed services. |
| ADR-002 | Accepted | Use Java 21, Spring Boot 3.5.x, Maven and MyBatis-Plus. | Matches the selected backend stack and existing-schema workflow. |
| ADR-003 | Accepted | Organize backend code by business feature. | Keeps controllers, services, DTOs and persistence code for one capability together. |
| ADR-004 | Accepted | Use PostgreSQL schema `lexpro` with the 32-table V3 baseline. | This is the reviewed normalized final database. |
| ADR-005 | Accepted | Return response DTOs, never persistence entities. | Prevents schema leakage and sensitive-field exposure. |
| ADR-006 | Accepted | Use resource JSON for success and `ProblemDetail` for errors. | Preserves HTTP semantics and gives the frontend stable error codes. |
| ADR-007 | Accepted | No public registration; accounts are administered internally. | Fits the internal legal-system use case. |
| ADR-008 | Proposed | Use Spring Security, BCrypt and JWT access tokens. | Supports stateless frontend authentication; exact lifetime is not yet fixed. |
| ADR-009 | Accepted | Store file binaries outside PostgreSQL behind a storage interface. | Supports local development and later MinIO without changing domain services. |
| ADR-010 | Accepted | Add a Python service only for document/AI processing. | Keeps Java authoritative for business data while using the appropriate AI ecosystem. |
| ADR-011 | Accepted | Defer vector dimension/index selection. | Dimension and operator must match the selected embedding model. |
| ADR-012 | Accepted | Do not model announcements, schedules or notifications in the current backend. | They are prototype-only and not part of the confirmed core database. |

## Pending decisions

- JWT access-token lifetime and whether refresh tokens are needed.
- Local storage root and production MinIO layout.
- Sensitive identity-field encryption/masking mechanism.
- Case status-transition matrix.
- AI providers and data-classification restrictions.

