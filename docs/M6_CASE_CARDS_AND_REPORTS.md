# M6 Case Cards and Reports

[中文版](zh-CN/M6_CASE_CARDS_AND_REPORTS.md)

> Status: implemented on 2026-07-30. M6 reuses the V1/V2/V3 baseline and adds no migration.

## Scope and storage

M6 covers asynchronous case-card generation and confirmation, report-template versions, asynchronous report generation, draft editing, review/finalization, normalized references, and DOCX/PDF export.

It uses the existing tables `case_card_fill_task`, `case_card_task_source`, `case_card_field`, `report_template`, `case_report`, `report_evidence`, `report_legal_element_result`, and `report_typical_case_reference`. Review-return reasons and all state-changing/export actions are recorded in `operation_log`; no report-revision table is added.

## JSON contract

Template content is an object containing one to fifty ordered sections. `code` is unique upper snake case; `instructions` is optional.

```json
{
  "sections": [
    {
      "code": "FACTS",
      "title": "案件事实",
      "instructions": "概述已确认的案件事实"
    }
  ]
}
```

Generated or edited report content uses the same ordered section list and replaces `instructions` with nonblank `content`.

```json
{
  "sections": [
    {
      "code": "FACTS",
      "title": "案件事实",
      "content": "经审查查明……"
    }
  ]
}
```

Section count, order, code and title must exactly match the selected template. The contract is checked when templates are created, model output is received, drafts are edited, reports are submitted, and files are exported.

## Lifecycles

| Resource | Transition | Rule |
|---|---|---|
| Template | `DRAFT -> ACTIVE` | Activating a draft atomically disables the prior `ACTIVE` version with the same `templateCode` |
| Template | `ACTIVE -> DISABLED` | A draft cannot be disabled directly; `DISABLED` is terminal and cannot be reactivated |
| Report | `GENERATING -> DRAFT` | Successful generation; the new version becomes current only now |
| Report | `GENERATING -> FAILED` | Failure is retained; the previous current report is unchanged |
| Report | `DRAFT -> REVIEWING` | Human review submission with optimistic `lockVersion` |
| Report | `REVIEWING -> DRAFT` | Human return with a required reason and `lockVersion` |
| Report | `REVIEWING -> FINAL` | Human finalization with `lockVersion`; `FINAL` is immutable |

## Authorization

| Operation | System permission | Case access |
|---|---|---|
| Read cards, jobs, reports or export | `CASE_READ` | Visible case |
| Generate a card/report | `REPORT_MANAGE` + `AI_EXECUTE` | `EDIT` |
| Confirm card fields/card, edit draft, submit or return review | `REPORT_MANAGE` | `EDIT` |
| Finalize a report | `REPORT_MANAGE` | `MANAGE` |
| Manage report templates | `REPORT_MANAGE` | Not case-scoped |

Authorization is enforced in both the controller and service layer. Cross-case sources are rejected by service checks and composite database foreign keys.

## Case cards and reports

Case-card sources are `DOCUMENT`, `ENTITY`, `LEGAL_ELEMENT`, and `SUMMARY`. Generated fields preserve the typed source ID, exact source quote, optional location/dossier and confidence. A field can be confirmed or rejected once; the whole card can be confirmed only after every field is resolved.

Report generation accepts an active template plus explicit case-card, evidence, legal-element and typical-case references. Evidence, legal elements and typical cases are stored in normalized reference tables. Regeneration creates a new version; a `GENERATING` or `FAILED` version never displaces the last successful current report.

The complete endpoint table is in [API_CONVENTIONS.md](API_CONVENTIONS.md). Public paths are under `/api/v1` and include case-card jobs/cards, report-template creation/activation/disablement, report jobs/history/detail/draft editing, review submission/return/finalization, and report export.

## Export

`GET /api/v1/cases/{caseId}/reports/{reportId}/exports/{format}` supports `DOCX` and `PDF` for `DRAFT`, `REVIEWING`, and `FINAL` reports. DOCX is editable. PDF wraps text and creates new A4 pages automatically. Non-final files display `草稿 - <status>`.

PDF export requires a readable Chinese TTF path:

```text
LEXPRO_REPORT_PDF_FONT_PATH=C:\Windows\Fonts\simhei.ttf
```

The path is local configuration and is never returned by the API. Missing or unreadable fonts produce `503` with `REPORT_PDF_FONT_NOT_CONFIGURED` or `REPORT_PDF_FONT_UNAVAILABLE`. Downloads use attachment disposition, `Cache-Control: no-store`, and `X-Content-Type-Options: nosniff`.

## Manual acceptance

1. Use a fictional case and a user with the permissions/access in the table above. Keep real case data away from external AI providers unless separately approved.
2. Create a template, activate it, create a second version, activate that version, and verify the first version becomes `DISABLED`.
3. Start a case-card job with each required typed source, poll its job endpoint, confirm/reject every field, and confirm the whole card.
4. Start a report job using the active template and explicit sources, poll until success, and verify the previous current report remains current until success.
5. Edit the draft using its returned `lockVersion`; retrying with the old value must return `409`.
6. Submit for review, return once with a reason, submit again, and finalize using a case `MANAGE` assignment. Further edits must return `409`.
7. Export DOCX and PDF, open both files, verify Chinese text and page layout, and verify non-final exports show the draft mark.
8. Query `operation_log` through an approved read-only check and verify generation, confirmation, transitions and exports are audited without prompts, secrets or local paths in API responses.
