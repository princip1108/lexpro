# LexPro Database Baseline

[中文版](zh-CN/DATABASE_BASELINE.md)

## Final baseline

- Database: PostgreSQL 15+ (local development currently uses PostgreSQL 18).
- Database name: `lexpro`.
- Application schema: `lexpro`.
- Extension: `pgvector` in `public`.
- Final business table count after all scripts: 32.
- After Flyway baseline registration, the schema contains 32 business tables plus the infrastructure table `flyway_schema_history`.

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
  AND table_type = 'BASE TABLE'
  AND table_name <> 'flyway_schema_history';
```

Expected business table count: `32`. Before Flyway baseline registration this is also the physical table count. Afterwards the schema has 33 physical tables because `flyway_schema_history` is Flyway infrastructure metadata. It is excluded from business validation and the ER diagram. Additional read-only checks are in `DBM/lexpro_schema_validation.sql` and `DBM/SCHEMA_UPGRADE_README.md`.

## Future Flyway strategy

1. Preserve the reviewed `DBM` V1/V2/V3 files exactly as the historical source.
2. Package derived classpath migrations with only the outer `BEGIN/COMMIT` removed, because Flyway owns each migration transaction. Each derived file records its source SHA-256.
3. Flyway and baseline-on-migrate are disabled by default through environment-controlled configuration.
4. Execute V1/V2/V3 on a disposable database and verify the final 32-table schema before touching the existing database.
5. Configure the existing non-empty development database as baseline version 3 only after backup, read-only validation and explicit approval.
6. Remove the one-time baseline flag immediately after the baseline record is created.
7. New changes start at `V4__<description>.sql`.
8. Every migration is forward-only, transactional where PostgreSQL permits, and accompanied by validation SQL.
9. Never use application startup as implicit approval to alter an unbacked-up production database.

Current controls:

```text
LEXPRO_FLYWAY_ENABLED=false
LEXPRO_FLYWAY_BASELINE_ON_MIGRATE=false
spring.flyway.clean-disabled=true
```

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
