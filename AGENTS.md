# LexPro AI Development Rules

[中文说明](AGENTS.zh-CN.md)

This file is the project-level source of instructions for AI-assisted development.

## Read before changing code

1. Read `docs/IMPLEMENTATION_PLAN.md` and the documentation for the affected module.
2. Inspect the actual frontend code and final V1/V2/V3 SQL before assuming fields or behavior.
3. Check the working tree and preserve user changes.
4. State the scope, affected files, and verification plan before editing.

## Source-of-truth order

1. Confirmed rules in `docs/BUSINESS_RULES.md` and `docs/DECISIONS.md`.
2. Final database scripts in `DBM/`, applied in V1, V2, V3 order.
3. Confirmed API contracts in `docs/API_CONVENTIONS.md` and OpenAPI.
4. Current application code and tests.
5. Frontend mock data, which expresses prototype behavior but is not authoritative business data.

When sources conflict, stop and document the conflict instead of silently choosing a new rule.

## Fixed technology baseline

- Java 21 and Spring Boot 3.5.x.
- Maven, MyBatis-Plus, Spring Security, PostgreSQL and Flyway.
- Vue 3, Vue Router, Element Plus and Vite.
- Package backend code by feature (`auth`, `user`, `casework`, `dossier`, `report`, etc.).
- Keep the Spring Boot application as a modular monolith. Add a separate Python service only when document/AI processing starts.

## Backend rules

- Use `Controller -> Service -> Mapper` boundaries.
- Controllers accept request DTOs and return response DTOs; never return persistence entities directly.
- Use constructor injection. Do not use field injection.
- Put transactions on service methods, with `readOnly = true` for queries.
- Validate all externally supplied data.
- Do not expose password hashes, identity numbers, internal storage paths, stack traces, or secrets.
- Use direct resource JSON for success, standard `ProblemDetail` for errors, and the shared page response for pagination.
- Use `/api/v1` for public application APIs and ISO-8601 timestamps with offsets.
- Enforce authorization and case-level data access in the service layer, not only in the frontend.
- Record security-sensitive and case-changing actions in `operation_log`.
- Do not introduce Redis, Kafka, Elasticsearch, microservices, or new infrastructure before an approved requirement needs it.

## Database rules

- The current baseline is exactly 32 tables after V1 + V2 + V3.
- V1/V2/V3 are one-time scripts and must never be rerun against the upgraded development database.
- Do not edit already-applied migration files for new requirements. Add a new migration after the baseline.
- Do not execute schema changes, destructive SQL, data deletion, or bulk updates without explicit user approval and a backup/validation plan.
- Keep `snake_case` in PostgreSQL and `camelCase` in Java/JSON.
- Repeated `case_id` discriminators that support composite foreign keys are intentional cross-case safety controls.
- `case_assignment.ended_at IS NULL` defines a current assignment.
- Do not add tables for announcements, schedules, notifications, task events, multiple user roles, or multiple user organizations unless requirements change.
- Do not fix vector dimensions or create vector indexes until the embedding model is approved.

## Security and secrets

- Store passwords with BCrypt only; never store or log plaintext passwords.
- Read secrets from environment variables. Never commit real `.env` files or credentials.
- The application has no public registration; administrators create accounts.
- Each user currently has one primary organization and one system role.
- Treat case files and personal identity data as sensitive.

## Verification required for every task

- Run focused tests for the changed behavior and the full Maven test suite when practical.
- Build the backend after backend changes and build the frontend after frontend changes.
- For database-facing behavior, verify against a test database or use read-only checks against development data.
- For APIs, verify the real HTTP status and response body.
- Check that sensitive fields are absent from responses.
- Update `docs/IMPLEMENTATION_PLAN.md` when a planned item changes state.
- When a core English document changes, update its matching `docs/zh-CN` document in the same task.
- Report what was verified and any remaining manual step or risk.

## Stop points requiring user approval

- Executing a migration or destructive command.
- Changing table structure or business status transitions.
- Adding infrastructure or an external service.
- Sending real case data to an external AI provider.
- Deploying, publishing, or changing production configuration.
