# LexPro

[中文说明](README.zh-CN.md)

LexPro is a legal case review application. The repository contains the Vue frontend, the Spring Boot backend, the final PostgreSQL schema, and the requirements/design source files.

## Repository layout

```text
lexpro/
|- src/                         # Vue 3 frontend
|- backend/lexpro-backend/      # Spring Boot backend
|- DBM/                         # PostgreSQL V1/V2/V3 scripts and ER documentation
|- docs/                        # Implementation and engineering rules
|- BACKEND_DEVELOPMENT_OVERVIEW.md
|- AGENTS.md
|- package.json
`- vite.config.js
```

## Current status

- Frontend: real authentication and route protection are connected; later business screens still use mock JSON where noted.
- Database: V1 + V2 + V3 complete, with 32 business tables in the `lexpro` schema.
- Backend: M0-M2 are complete, including JWT authentication, user administration, organization/RBAC queries, standard errors, OpenAPI, request correlation and audit logging.
- Case workflows, dossier storage, AI processing, reports, and recommendation APIs are not implemented yet.

## Documentation

- [Chinese documentation index](docs/zh-CN/README.md)
- [Implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [API conventions](docs/API_CONVENTIONS.md)
- [Business rules](docs/BUSINESS_RULES.md)
- [Database baseline](docs/DATABASE_BASELINE.md)
- [Technical decisions](docs/DECISIONS.md)
- [Backend setup](backend/README.md)
- [M2 security review](docs/M2_SECURITY_REVIEW.md)
