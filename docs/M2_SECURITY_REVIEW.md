# M2 Authentication Security Review

[中文说明](zh-CN/M2_SECURITY_REVIEW.md)

> Reviewed: 2026-07-29
> Scope: authentication, user administration, organization/RBAC queries and frontend session handling.

## Implemented controls

- Only health checks, local API documentation and login are public; all other API paths require authentication.
- User, organization, role and permission administration requires `USER_MANAGE` at the controller method boundary.
- Every protected request verifies the HS256 signature, issuer, expiry, current account status, account update time and current database permissions.
- Passwords use BCrypt cost 12. New/reset passwords require 12 characters, mixed case, a number and a special character and are limited to 72 UTF-8 bytes.
- Unknown user, wrong password and disabled account return the same `INVALID_CREDENTIALS` response. A dummy BCrypt comparison reduces username timing differences.
- Login success/failure, logout, bootstrap, user creation, status changes and password resets are audited without passwords or tokens.
- Failed-login audit uses an independent transaction so the 401 exception cannot roll it back.
- DTOs and frontend state exclude password hashes. Authentication responses use `Cache-Control: no-store`.
- The development administrator bootstrap is off by default, only runs when no users exist and never logs its password.
- The frontend removes hardcoded credentials, checks JWT expiry before routing and clears its session on API 401.

## Deliberate limitations

- There is no refresh token or server-side token blacklist. Logout is client-side invalidation plus audit; a copied token can remain valid until expiry unless the account changes.
- Access tokens are stored in web storage. The default is `sessionStorage`; selecting "keep me signed in" uses `localStorage`. XSS prevention therefore remains important.
- HS256 uses one shared application secret. Production must inject a unique high-entropy secret and rotate it through deployment configuration.
- Login rate limiting and account lockout are not included because no approved proxy/rate-limit infrastructure exists yet. Production deployment must provide an edge rate limit before internet exposure.
- The existing development database was not modified during M2 implementation. A first administrator still requires the explicitly enabled one-time bootstrap.

## Verification evidence

- Backend unit and MVC tests cover BCrypt, safe bootstrap, generic login failure, JWT claims, 401/403 `ProblemDetail`, user administration, audit content, organization cycles and application context loading.
- The full Maven test suite and backend package build pass.
- Frontend JavaScript syntax checks and the Vite production build pass.
- A real login HTTP smoke test remains a manual step until the user explicitly approves inserting the first development administrator.
