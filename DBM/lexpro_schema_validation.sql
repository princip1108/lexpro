-- LexPro database installation validation

SELECT
    (
        SELECT extversion
        FROM pg_extension
        WHERE extname = 'vector'
    ) AS vector_version,
    '[1,2,3]'::public.vector AS vector_test,
    (
        SELECT count(*)
        FROM pg_tables
        WHERE schemaname = 'lexpro'
          AND tablename <> 'flyway_schema_history'
    ) AS table_count;
