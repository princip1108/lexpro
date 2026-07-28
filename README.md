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

- Frontend prototype: implemented with mock JSON data.
- Database: V1 + V2 + V3 complete, 32 tables in the `lexpro` schema.
- Backend: starts successfully, connects to PostgreSQL, exposes health checks and a read-only user list.
- Authentication, case workflows, dossier storage, AI processing, reports, and recommendation APIs are not implemented yet.

## Documentation

- [Chinese documentation index](docs/zh-CN/README.md)
- [Implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [API conventions](docs/API_CONVENTIONS.md)
- [Business rules](docs/BUSINESS_RULES.md)
- [Database baseline](docs/DATABASE_BASELINE.md)
- [Technical decisions](docs/DECISIONS.md)
- [Backend setup](backend/README.md)
