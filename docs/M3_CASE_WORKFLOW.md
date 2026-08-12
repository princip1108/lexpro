# M3 Case Workflow

[中文版](zh-CN/M3_CASE_WORKFLOW.md)

## Delivered scope

- Backend `casework` module with `Controller -> Service -> Mapper` boundaries.
- Case list/detail/create/update APIs under `/api/v1/cases`.
- Party list/create/update APIs under `/api/v1/cases/{caseId}/parties`.
- Assignment history/create/end APIs under `/api/v1/cases/{caseId}/assignments`.
- Frontend `TodoCases` and the first dashboard case panels now call real case APIs.
- Case-changing operations write `operation_log` audit events.

## Case access

- A user can see a case when they created it or currently have a `case_assignment` row with `ended_at IS NULL`.
- `CASE_READ` is required for reads.
- `CASE_WRITE` is required for case/party writes, and the service layer requires at least `EDIT` case access.
- `CASE_ASSIGN` is required for assignment changes, and the service layer requires `MANAGE` case access.
- Unauthorized or inaccessible cases are returned as `404` to avoid leaking case existence.

## Current behavior

- New cases are created with status `PENDING`.
- The creator automatically receives a current `ASSIGNEE` assignment with `MANAGE` access.
- Case list supports pagination, keyword search, status filter, case-type filter and overdue filter.
- A case is overdue when `deadline_at < CURRENT_TIMESTAMP` and status is not `CLOSED` or `ARCHIVED`.
- Ordinary case update changes metadata only. It does not change `case_status`.
- Assignment creation accepts only active users and rejects duplicate current assignments at database/service level.
- Party APIs never query or return raw `identity_number` or `identity_number_hash`.

## Deferred decisions

- Status-transition matrix and authorized transition roles.
- Case-number generation policy beyond the existing uniqueness protection.
- Identity-number encryption, key source, hash/search behavior, masking format and edit rules.

## Manual acceptance

1. Start PostgreSQL, backend and frontend with a real development administrator.
2. Login with a user that has `CASE_READ`, `CASE_WRITE` and `CASE_ASSIGN`.
3. Open `/todo-cases`, create a case, confirm it appears as `PENDING`.
4. Edit the case metadata and confirm status did not change.
5. Add a party and confirm the response contains `identityNumberMasked` only when data exists, never raw identity fields.
6. Add and end an assignment for an active user.
7. Use a user without assignment to confirm the case is not visible.
8. Check `operation_log` contains create/update/party/assignment events.
