# LexPro Backend

[中文说明](README.zh-CN.md)

## Stack

- Java 21
- Spring Boot 3.5.16
- Maven
- MyBatis-Plus 3.5.17
- PostgreSQL
- Flyway
- SpringDoc OpenAPI

The Maven project is in `backend/lexpro-backend`.

## Required environment

The local DBeaver connection uses database `lexpro` on `127.0.0.1:5432`. Configure the following environment variable in the IntelliJ run configuration:

```text
LEXPRO_DB_PASSWORD=<your PostgreSQL password>
LEXPRO_JWT_SECRET=<a unique random value of at least 32 bytes>
```

Optional overrides:

```text
LEXPRO_DB_URL=jdbc:postgresql://127.0.0.1:5432/lexpro?currentSchema=lexpro
LEXPRO_DB_USERNAME=postgres
LEXPRO_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
LEXPRO_JWT_ISSUER=https://lexpro.local
LEXPRO_JWT_ACCESS_TOKEN_TTL=PT30M
```

Do not put a real database password, JWT secret or administrator password in `application.properties` or commit it to Git. Changing the JWT secret invalidates all issued access tokens.

## First development administrator

The bootstrap is disabled by default and only creates an administrator when `app_user` is empty. For a first local run, temporarily add these variables to the IntelliJ run configuration:

```text
LEXPRO_BOOTSTRAP_ADMIN_ENABLED=true
LEXPRO_BOOTSTRAP_ADMIN_USERNAME=admin
LEXPRO_BOOTSTRAP_ADMIN_PASSWORD=<a strong local password>
LEXPRO_BOOTSTRAP_ADMIN_REAL_NAME=System Administrator
LEXPRO_BOOTSTRAP_ORGANIZATION_CODE=LEXPRO
LEXPRO_BOOTSTRAP_ORGANIZATION_NAME=LexPro
```

The password must have at least 12 characters with uppercase, lowercase, number and special characters. Start once, confirm the account was created, then set `LEXPRO_BOOTSTRAP_ADMIN_ENABLED=false`. Existing user data is never overwritten.

## Flyway safety

The existing development database was registered as Flyway baseline version 3 on 2026-07-28. Flyway remains disabled by default so a migration still requires an explicit decision:

```text
LEXPRO_FLYWAY_ENABLED=false
LEXPRO_FLYWAY_BASELINE_ON_MIGRATE=false
```

Keep `LEXPRO_FLYWAY_BASELINE_ON_MIGRATE=false`. Enable Flyway only for an approved validation or migration; new schema work starts at V4. The current database contains 32 business tables and the `lexpro.flyway_schema_history` infrastructure table.

## Run in IntelliJ IDEA

1. Open `backend/lexpro-backend` as the Maven project.
2. Select Java 21.
3. Open **Run -> Edit Configurations** and add `LEXPRO_DB_PASSWORD` and `LEXPRO_JWT_SECRET` under environment variables.
4. Run `com.lexpro.lexprobackend.LexproBackendApplication`.

## Command line

With Java 21 available in `JAVA_HOME`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

## Current endpoints

```http
GET /api/health
GET /api/health/database
POST /api/v1/auth/login
GET /api/v1/auth/me
POST /api/v1/auth/logout
GET /api/v1/users?page=1&size=20
GET /api/v1/users/{userId}
POST /api/v1/users
PATCH /api/v1/users/{userId}/status
PUT /api/v1/users/{userId}/password
GET /api/v1/organizations/tree
GET /api/v1/roles
GET /api/v1/permissions
```

Only health, Swagger/OpenAPI and login are public. User, organization, role and permission APIs require `USER_MANAGE`.

Access tokens expire after 30 minutes by default and have no refresh token. Logout records an audit event and the frontend discards the token; it does not maintain a server-side blacklist.

Local API documentation:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

## Common API foundation

- Errors use `application/problem+json` with stable `errorCode`, `requestId`, and optional `fieldErrors` properties.
- Pagination starts at page 1, defaults to 20 items, and is limited to 100 items.
- Every response includes `X-Request-Id`; a safe client-provided ID is preserved.
- Request completion logs include method, path, status, duration, and request ID.
- `AuditService` writes security-sensitive or state-changing events to the existing `operation_log` table.
- CORS is limited to the configured Vue development origins and `/api/**` routes.

## Expected database check

`GET /api/health/database` should return database `lexpro` and business table count `32`. The endpoint deliberately excludes Flyway's `flyway_schema_history` infrastructure table.
