# M7 Typical-Case Recommendation

[Chinese](zh-CN/M7_TYPICAL_CASE_RECOMMENDATION.md)

## Delivered boundary

- Java authorizes the operational case, writes the corpus/recommendation/favorite/audit data and returns DTOs.
- Python normalizes and embeds imported cases, reads only the typical-case corpus, and performs structured, lexical and vector recall, RRF fusion, reranking and reason generation.
- Embeddings run locally with `BAAI/bge-m3`, dimension `1024` and cosine distance. No DeepSeek key is used and case text is not sent to an external model.
- Python can degrade vector failures to a marked lexical-only result. Service/database failures return `503` and do not create a recommendation batch.

## Internal contract

- `POST /internal/v1/normalize` accepts contract `1.0`, a request ID and 1-50 corpus items; it returns normalized fields, one 1024-dimensional embedding per item and model provenance.
- `POST /internal/v1/retrieve` accepts contract `1.0`, the authorized fact snapshot, dispute focus, structured filters and a 1-50 limit; it returns the same request ID, model/index/pipeline provenance, degradation state, query embedding and contiguous unique ranks with bounded scores and structured reasons.
- Java rejects a schema/request/model/dimension/rank mismatch with `502 RETRIEVAL_RESPONSE_INVALID`. Network and Python 5xx failures are retried once; Python 4xx responses are not retried. A final transport failure returns stable `503` and writes an independent failed audit event.

## One-time database preparation

V4 is forward-only and does not add a business table. It fixes the two existing vector columns to `vector(1024)` and creates `ix_typical_case_embedding_hnsw`. Back up and validate the development database before explicitly applying:

```sql
SELECT vector_dims(embedding), count(*)
FROM lexpro.typical_case
WHERE embedding IS NOT NULL
GROUP BY vector_dims(embedding);

SELECT vector_dims(query_fact_embedding), count(*)
FROM lexpro.case_recommendation
WHERE query_fact_embedding IS NOT NULL
GROUP BY vector_dims(query_fact_embedding);
```

The reviewed scripts are `DBM/lexpro_schema_upgrade_v4_retrieval.sql` and Flyway `V4__configure_typical_case_vectors.sql`. They were not executed automatically during M7 delivery.

Create a separate read-only login for Python after approval; replace the placeholder password locally:

```sql
CREATE ROLE lexpro_retrieval LOGIN PASSWORD '<strong-local-password>';
GRANT CONNECT ON DATABASE lexpro TO lexpro_retrieval;
GRANT USAGE ON SCHEMA lexpro TO lexpro_retrieval;
GRANT SELECT ON lexpro.typical_case, lexpro.typical_case_content TO lexpro_retrieval;
ALTER ROLE lexpro_retrieval SET default_transaction_read_only = on;
```

Do not grant access to `case_record`, users, dossiers, recommendations or audit tables.

## Run the Python service

From `backend/retrieval-service`:

```powershell
D:\python\python.exe -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
$env:LEXPRO_RETRIEVAL_DATABASE_URL='postgresql://lexpro_retrieval:<password>@127.0.0.1:5432/lexpro'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8010
```

The first embedding request downloads the local BGE-M3 weights. For a controlled environment, set `LEXPRO_RETRIEVAL_MODEL_REVISION` to an approved immutable local/Hugging Face revision and set the same value in Java as `LEXPRO_RETRIEVAL_MODEL_VERSION`.

## Run Java with retrieval

Add these variables to the IntelliJ run configuration:

```text
LEXPRO_RETRIEVAL_ENABLED=true
LEXPRO_RETRIEVAL_BASE_URL=http://127.0.0.1:8010
LEXPRO_RETRIEVAL_MODEL_NAME=BAAI/bge-m3
LEXPRO_RETRIEVAL_MODEL_VERSION=main
```

Timeouts default to 3 seconds connect, 20 seconds read and one retry. Import requires `REPORT_MANAGE + AI_EXECUTE`; search, favorites and recommendations require `RECOMMENDATION_USE`; case recommendations additionally require `CASE_READ` and Java case visibility.

## Acceptance

1. `GET http://127.0.0.1:8010/internal/v1/health` returns `UP` and the 1024/cosine model contract.
2. Import at least two typical cases through `/api/v1/typical-cases/imports`; importing the same `externalCaseId` again updates instead of duplicating it.
3. Create a recommendation through `/api/v1/cases/{caseId}/recommendations`, then read its history/detail and favorite one result.
4. Confirm `case_recommendation`, `case_recommendation_item`, `typical_case_favorite` and `operation_log` contain the expected traceability data and no response exposes embeddings or database credentials.
