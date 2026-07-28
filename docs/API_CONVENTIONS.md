# LexPro API Conventions

## General

- Base path: `/api/v1`.
- JSON field names: `camelCase`.
- Database names remain `snake_case` and are not exposed as an API convention.
- Content type: `application/json; charset=UTF-8`, except file upload/download.
- Timestamps: ISO-8601 with an offset, for example `2026-07-28T10:30:00+08:00`.
- Dates: ISO `yyyy-MM-dd`.

## HTTP semantics

| Operation | Status |
|---|---:|
| Successful query/update | `200 OK` |
| Resource created | `201 Created` |
| Async work accepted | `202 Accepted` |
| Successful operation with no body | `204 No Content` |
| Invalid input | `400 Bad Request` |
| Missing/invalid authentication | `401 Unauthorized` |
| Authenticated but forbidden | `403 Forbidden` |
| Resource not found | `404 Not Found` |
| State/version conflict | `409 Conflict` |
| Unsupported file/media | `415 Unsupported Media Type` |
| Unexpected failure | `500 Internal Server Error` |

Successful responses return the resource or operation result directly. Do not wrap every response in `{code, message, data}`.

## Errors

Errors use Spring `ProblemDetail` (`application/problem+json`) with stable extensions:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid",
  "instance": "/api/v1/cases",
  "errorCode": "VALIDATION_FAILED",
  "requestId": "01J...",
  "fieldErrors": {
    "caseName": "must not be blank"
  }
}
```

`errorCode` is stable for frontend logic; `detail` is for display/diagnostics. Never expose stack traces, SQL, passwords, tokens or storage paths.

## Pagination

- Request: `page` starts at 1, `size` defaults to 20 and is limited to 100.
- Sorting must use an endpoint-specific allowlist; never pass raw field names into SQL.

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

## Authentication

- Protected APIs use `Authorization: Bearer <access-token>`.
- Public endpoints are limited to health checks, local API documentation and login.
- The backend is authoritative for permissions and case visibility.

## Endpoint naming

- Use plural nouns: `/cases`, `/users`, `/files`.
- Use nested resources where the parent authorization boundary matters: `/cases/{caseId}/parties`.
- Use explicit command resources for business transitions: `/cases/{caseId}/status-transitions`.
- Do not encode verbs such as `getUserList` in resource paths.

