# LexPro

> For a complete fresh-machine setup, database initialization, model-server tunneling and acceptance checklist, see [部署运行说明.md](部署运行说明.md).

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

- Frontend: authentication, route protection, dashboard, case workflow, workspace, organization/users, dossier parsing, intelligent-result history, summaries, reports and typical-case recommendations use the real backend APIs. AI generation, report generation and new retrieval runs still require their documented runtime switches and services.
- Database: V1 + V2 + V3 complete, with 32 business tables in the `lexpro` schema.
- Backend: M0-M7 and the initial M8 read-only MCP Server are implemented, including typical-case hybrid retrieval and a JWT-protected `/mcp` endpoint.
- Still deferred: case status transitions, raw identity-number handling, production MinIO, PDF/Office parsing, named MCP-client acceptance and any MCP mutation. External case-data transfer remains disabled pending explicit approval.

## Documentation

- [Chinese documentation index](docs/zh-CN/README.md)
- [Implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [API conventions](docs/API_CONVENTIONS.md)
- [Business rules](docs/BUSINESS_RULES.md)
- [Database baseline](docs/DATABASE_BASELINE.md)
- [Technical decisions](docs/DECISIONS.md)
- [AI retrieval and MCP architecture](docs/AI_RETRIEVAL_AND_MCP_ARCHITECTURE.md)
- [Backend setup](backend/README.md)
- [Local paths and configuration](docs/LOCAL_PATHS_AND_CONFIGURATION.md)
- [M2 security review](docs/M2_SECURITY_REVIEW.md)
- [M3 case workflow](docs/M3_CASE_WORKFLOW.md)
- [M4 dossier management](docs/M4_DOSSIER_MANAGEMENT.md)
- [M5 document processing](docs/M5_DOCUMENT_PROCESSING.md)
- [M6 case cards and reports](docs/M6_CASE_CARDS_AND_REPORTS.md)
- [Manual actions and acceptance](docs/MANUAL_ACTIONS.md)
