# M4 Dossier Management

[Chinese](zh-CN/M4_DOSSIER_MANAGEMENT.md)

## Delivered scope

- A `DossierStorage` boundary with a local filesystem adapter for development.
- Case-scoped folder trees and tags using the existing V2 tables.
- Streaming upload with a configurable size limit, extension/MIME allowlists, common file-signature checks and SHA-256 hashing.
- Metadata list, authenticated attachment download, rename/move/tag replacement, soft deletion and restore.
- Service-layer case authorization and audit events for every change and download.
- No database migration and no physical deletion during normal dossier lifecycle operations.

## Authorization

- Folder, tag and file reads require `CASE_READ` and case `VIEW` access.
- Downloads require the same read checks and are audited.
- Folder/tag creation and file upload/update/delete/restore require `DOSSIER_MANAGE` and case `EDIT` access.
- An inaccessible case is reported as `404`, matching M3 and avoiding case-existence disclosure.

## Storage and security

The local adapter stores content under a configured root using an internal random key such as `cases/9/<uuid>.pdf`. The original filename is metadata only. Object keys and filesystem paths never appear in API responses.

Defaults for development:

```text
LEXPRO_DOSSIER_LOCAL_ROOT=./storage
LEXPRO_DOSSIER_MAX_FILE_SIZE=25MB
LEXPRO_DOSSIER_MAX_REQUEST_SIZE=26MB
LEXPRO_DOSSIER_ALLOWED_EXTENSIONS=pdf,doc,docx,xls,xlsx,ppt,pptx,txt,jpg,jpeg,png
```

`LEXPRO_DOSSIER_ALLOWED_CONTENT_TYPES` can override the MIME allowlist. The application validates declared size, streamed size, extension, MIME type and common file signatures. This is an upload boundary, not malware scanning; production antivirus/content-disarm requirements remain open.

If storage succeeds and the database operation then fails inside the service call, the newly written object is removed as compensation. Soft deletion changes only `evidence_file.file_status/deleted_at/deleted_by`; it intentionally retains binary content so restore remains possible.

## Deferred work

- Production storage layout, MinIO adapter and credentials.
- Retention duration, permanent purge and backup/restore policy.
- Antivirus or content-disarm scanning requirements.
- Asynchronous parsing and AI processing, which begin in M5.

## Manual acceptance

1. Configure the database/JWT variables and optionally `LEXPRO_DOSSIER_LOCAL_ROOT` to an absolute development directory.
2. Start the backend and log in as a user with `CASE_READ`, `DOSSIER_MANAGE` and an `EDIT` assignment to a case.
3. In Swagger UI, create a folder and tag, upload a small allowed file, list metadata and download it.
4. Confirm metadata JSON has no `fileUrl`, object key or storage path and the SHA-256 value has 64 hexadecimal characters.
5. Soft delete the file, confirm download returns `410`, restore it and download it again.
6. Confirm `operation_log` contains folder/tag/file change events and the download event.
