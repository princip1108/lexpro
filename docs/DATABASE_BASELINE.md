# LexPro Database Baseline

[中文版](zh-CN/DATABASE_BASELINE.md)

## Final baseline

- Database: PostgreSQL 15+ (local development currently uses PostgreSQL 18).
- Database name: `lexpro`.
- Application schema: `lexpro`.
- Extension: `pgvector` in `public`.
- Final table count after all scripts: 32.

The one-time scripts are:

```text
DBM/lexpro_schema_postgresql.sql             # V1, 21 tables
DBM/lexpro_schema_upgrade_v2_core.sql         # V2, +5 tables
DBM/lexpro_schema_upgrade_v3_workspace.sql    # V3, +6 tables
```

The existing development database has already been upgraded. Do not rerun these scripts against it.

## Validation

```sql
SELECT current_database(), current_user;

SELECT count(*)
FROM information_schema.tables
WHERE table_schema = 'lexpro'
  AND table_type = 'BASE TABLE';
```

Expected count: `32`. Additional read-only checks are in `DBM/lexpro_schema_validation.sql` and `DBM/SCHEMA_UPGRADE_README.md`.

## Future Flyway strategy

1. Preserve V1/V2/V3 exactly as the historical baseline.
2. Configure the existing non-empty development database as baseline version 3 only after backup and review.
3. New changes start at `V4__<description>.sql`.
4. Every migration is forward-only, transactional where PostgreSQL permits, and accompanied by validation SQL.
5. Test a migration on a disposable/test database before development or production.
6. Never use application startup as implicit approval to alter an unbacked-up production database.

## Design invariants

- Primary keys are identity `bigint` values.
- Business timestamps use `timestamptz`.
- Status values are constrained by database checks.
- User role and organization are normalized; one of each per user.
- Composite `(entity_id, case_id)` keys prevent cross-case references.
- Current assignment uses `ended_at IS NULL`.
- Current parse/report/summary versions use their documented partial unique indexes.
- Repeated `case_id` columns used for cross-case integrity are intentional, not denormalization mistakes.

## Vector fields

`typical_case.embedding` and `case_recommendation.query_fact_embedding` remain dimensionless until the embedding model is approved. A later migration must set the same dimension on both columns and create the matching cosine HNSW index.
