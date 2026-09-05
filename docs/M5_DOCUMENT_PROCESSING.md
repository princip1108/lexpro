# M5 Document Processing

[Chinese](zh-CN/M5_DOCUMENT_PROCESSING.md)

## Delivered scope

### M5-S1 parsing

- Versioned `document_parse_result` workflow using the existing V1/V2 schema.
- `202 Accepted` start/retry API, history API and result-detail API.
- Per-file transactional locking, one current result, monotonically increasing versions and fresh-job conflict protection.
- Post-commit asynchronous scheduling through a bounded local executor.
- Stable success/failure states, queue-rejection handling and retry recovery for stale interrupted jobs.
- Parser interface plus a local UTF-8 `.txt` development parser.
- Disabled-by-default internal adapter for PDF, DOCX and JPEG/PNG files, with an authenticated Java-to-Python boundary and validated versioned positioning contract. DOCX text/tables are extracted directly; embedded PNG/JPEG images use MinerU.
- Case-level authorization and audit events correlated with the original request ID.

### M5-S2 entity recognition

- Preferred internal LexPro_8B adapter using the authenticated Python AI-service boundary; the prior OpenAI-compatible DeepSeek adapter remains a disabled-by-default fallback.
- Exactly six accepted types: `SUSPECT`, `LOCATION`, `ORGANIZATION`, `TIME`, `CRIME` and `DRUG`. Any other model category invalidates the response.
- Versioned `lexpro.entity.v2` output with source SHA-256, occurrence-preserving block/document UTF-16 offsets and aggregated display values.
- Bounded post-commit asynchronous execution; the frontend receives `202 Accepted` and polls a persistent audit-derived job status.
- Original model JSON and separate first-write human-confirmed JSON in the existing `entity_result` table.
- Model, response model, prompt/schema versions, prompt snapshot, generation parameters, token usage, request ID and duration are preserved.
- Case-level authorization and `AI_EXECUTE` checks for generation and confirmation.
- Stable provider/response/queue failure codes without credentials, provider bodies, prompts or source text in logs or error responses.
- No migration: the reviewed V1/V2/V3 schema already contains every required field.

Automated tests and builds send no data to either model server. The internal adapter is disabled by default; the legacy DeepSeek route additionally requires explicit external-transfer approval.

### M5-S3 legal elements

- Shared structured-JSON AI transport with a legal-element-specific prompt and validator.
- Existing `legal_element_result` storage for immutable model output, validation report, provenance and first-write human confirmation.
- A non-empty evidence list for every element, with exact source quotes. The v2 DeepSeek adapter asks the provider for quotes only; Java deterministically adds validated UTF-16 offsets from the source text before persistence.
- Same-case document lookup, service-layer case authorization, `AI_EXECUTE` checks and audit-derived asynchronous job status.
- Provider output is rejected before persistence when element codes, confidence, evidence or offsets are invalid.

### M5-S4 case summaries

- Explicit `FACT`, `PROCESS`, `CONCLUSION` and `FULL` summary types, generated from one to twenty unique selected parse results.
- Every selected source must belong to the requested case and contain successful extracted text.
- Versions are scoped by case and summary type. Case-row locking serializes version allocation and current-version changes.
- The previous current summary stays current while generation runs and when generation fails; the flag changes only in the successful insert transaction.
- Confirmation records who accepted the generated text and when. It does not overwrite text; regeneration creates another version.
- Model provenance and the selected source IDs are retained without returning the stored prompt snapshot through application APIs.

## State and retry rules

```text
POST start -> PROCESSING -> SUCCESS
                         -> FAILED
FAILED + POST again -> new PROCESSING version
fresh PROCESSING + POST again -> 409 PARSE_IN_PROGRESS
stale PROCESSING + POST again -> old FAILED, new PROCESSING version
```

The current-result flag moves to the newly created version. Previous content and failure records remain immutable history. There is no unbounded automatic retry loop.

## Parser configuration

The built-in parser reads active UTF-8 text dossier files. When the internal AI service is disabled, PDF, Word and image files fail with `PARSER_UNAVAILABLE_FOR_FILE_TYPE`. When enabled, the Spring adapter reads the private dossier object, enforces the configured size limit, and sends raw bytes to the internal Python service. The Python service calls MinerU and returns canonical text plus block/page/bounding-box provenance. Java validates the schema, SHA-256 digest, UTF-16 offsets, block ordering and exact block substrings before persistence.

```text
LEXPRO_PROCESSING_CORE_THREADS=2
LEXPRO_PROCESSING_MAX_THREADS=4
LEXPRO_PROCESSING_QUEUE_CAPACITY=50
LEXPRO_PROCESSING_MAX_EXTRACTED_CHARS=2000000
LEXPRO_PROCESSING_STALE_AFTER=PT30M
LEXPRO_AI_SERVICE_ENABLED=false
LEXPRO_AI_SERVICE_BASE_URL=http://127.0.0.1:8020
LEXPRO_AI_SERVICE_INTERNAL_TOKEN=<shared internal secret, at least 32 characters>
LEXPRO_AI_SERVICE_CONNECT_TIMEOUT=PT5S
LEXPRO_AI_SERVICE_READ_TIMEOUT=PT15M
LEXPRO_AI_SERVICE_MAX_FILE_SIZE=25MB
```

The parser receives an internal object key through Java only. APIs return extracted content and provenance, never the object key or local path. Queue saturation produces `PROCESSING_QUEUE_FULL`; unexpected internal failures produce a stable `DOCUMENT_PROCESSING_FAILED` code while stack traces remain server-side.

DOCX is not forwarded wholesale to MinerU. The adapter reads OOXML paragraph/table text in document order, OCRs embedded PNG/JPEG at their logical occurrence, and appends auxiliary header/footer/note text once. It returns `lexpro.parse.v2` with `parser=docx`, `parserVersion=docx-ooxml/2+<MinerU version>`, exact UTF-16 blocks and null page/bbox coordinates: this is logical text positioning, not Word layout reconstruction. Plain-text DOCX needs no model call. ZIP entry count/uncompressed size, XML size/DTD, text length and image count are bounded; external image URLs are never fetched. Corrupt/encrypted files, embedded objects and unsupported image formats produce safe `WORD_*` errors. Legacy `.doc` returns `LEGACY_WORD_CONVERSION_REQUIRED`: save as DOCX/PDF first. Upstream 5xx errors map to `MINERU_SERVICE_UNAVAILABLE`, not request rejection. No database migration or model-server modification is needed.

A successful, structurally valid embedded-image OCR response with no text no longer invalidates the Word document. Existing text is retained and `parsedText.warnings` records `{code: "WORD_IMAGE_NO_TEXT", imageIndex: <one-based occurrence>}` without an internal image path; the frontend shows a completion notice. The original uploaded document still contains the image. Transport failures and malformed responses remain errors; a document with no text anywhere returns `WORD_DOCUMENT_EMPTY`. Standalone PDF/image empty-result behavior is unchanged.

## DeepSeek configuration and data boundary

DeepSeek is used only after trusted text extraction and does not replace PDF/Office parsing. The API key is read only from the environment. `LEXPRO_AI_ENABLED=true` enables the adapter, while the separate `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true` switch authorizes sending parsed case text. The second switch stays `false` until the project owner explicitly accepts the data-classification and provider risk.

```text
LEXPRO_AI_ENABLED=false
LEXPRO_AI_BASE_URL=https://api.deepseek.com
LEXPRO_AI_API_KEY=<local secret>
LEXPRO_AI_MODEL=deepseek-v4-flash
LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=false
LEXPRO_AI_CONNECT_TIMEOUT=PT5S
LEXPRO_AI_READ_TIMEOUT=PT60S
LEXPRO_AI_MAX_INPUT_CHARS=60000
LEXPRO_AI_MAX_OUTPUT_TOKENS=4096
```

The internal entity schema is `lexpro.entity.v2`. Each occurrence requires one of the six types, exact source text, a block ID, block-relative UTF-16 offsets and document-relative UTF-16 offsets. Java recalculates the block origin, rejects surrogate-pair splits and requires both slices to equal the entity text before persistence. Repeated occurrences remain separate; `documentEntities` is only the aggregated display view. Human confirmation writes `final_entities_json` once and never overwrites `entities_json`.

Legal-element output is an object containing a non-empty `elements` array. Each element uses an allowlisted code and contains analysis plus at least one exact source quote. The provider must not calculate offsets; the v2 adapter locates each quote in the source (preserving repeated-quote order), writes Java UTF-16 `startOffset`/`endOffset`, and then validates them against the same source text. Summary generation accepts only an allowlisted type and explicitly selected, successful same-case parse results.

## Later M5 work

Still pending outside the completed S1-S4 code scope:

- Approve real-case-data transfer and production timeout/concurrency limits; current values are development defaults.
- Complete fictional-document acceptance against the real MinerU and LexPro model servers, then approve production limits and deployment settings.

## Manual acceptance

1. Upload a small UTF-8 `.txt` file to a case using M4.
2. Use a user with `AI_EXECUTE` and case `EDIT` access to call the parse-job start endpoint.
3. Confirm the response is `202` with `PROCESSING`, then poll the returned `Location` until it is `SUCCESS`.
4. Confirm `rawText`, `parserVersion=local-text/1`, version and request ID are present, while `fileUrl` and storage paths are absent.
5. Start the same file again after completion and confirm a new version is created and history preserves the old result.
6. With `LEXPRO_AI_SERVICE_ENABLED=false`, try a PDF and confirm the task becomes `FAILED` with `PARSER_UNAVAILABLE_FOR_FILE_TYPE`.
7. After configuring the internal service with a fictional PDF, enable the switch and confirm the result reports `parserVersion`, preserves page/block provenance in `structuredData`, and contains no storage path. Confirm emoji and supplementary CJK characters remain exact when sliced by the returned UTF-16 offsets.
8. With both adapters disabled, start entity recognition and confirm `503 AI_PROVIDER_DISABLED`. With only the legacy DeepSeek adapter enabled and external transfer disabled, confirm `503 AI_DATA_EXPORT_DISABLED` and no provider call.
9. Only with approved fictional/masked test data, set `LEXPRO_AI_ALLOW_EXTERNAL_CASE_DATA=true`, restart, start recognition, poll the returned job `Location`, and inspect the entity-result history.
10. Confirm one result with an edited `finalEntities` object. Verify the original output remains unchanged and a second confirmation returns `409 ENTITY_RESULT_ALREADY_CONFIRMED`.
11. Start a legal-element job for the same parsed document, poll its `Location`, and confirm every returned element has an exact source quote. Confirming edited elements with an invented quote or mismatched offsets must return `400 LEGAL_ELEMENT_RESULT_INVALID`.
12. Confirm a valid legal-element result once. Verify the original model output remains unchanged and a second confirmation returns `409 LEGAL_ELEMENT_ALREADY_CONFIRMED`.
13. Start a `FULL` summary using one or more successful `sourceDocIds`, poll its `Location`, and verify version `1` is current. Generate it again and verify version `2` becomes current while version `1` remains in history.
14. Confirm one summary once and verify a second confirmation returns `409 CASE_SUMMARY_ALREADY_CONFIRMED`. Trigger a failed generation and verify the last successful summary remains current.
