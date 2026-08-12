-- LexPro V4: fix the approved retrieval vector specification.
-- Prerequisite: pgvector is installed and V1/V2/V3 have already been applied.
-- Model: BAAI/bge-m3, dimension: 1024, distance: cosine.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM lexpro.typical_case
        WHERE embedding IS NOT NULL AND public.vector_dims(embedding) <> 1024
    ) OR EXISTS (
        SELECT 1 FROM lexpro.case_recommendation
        WHERE query_fact_embedding IS NOT NULL AND public.vector_dims(query_fact_embedding) <> 1024
    ) OR EXISTS (
        SELECT 1 FROM lexpro.typical_case
        WHERE embedding IS NOT NULL
          AND (embedding_model_name IS DISTINCT FROM 'BAAI/bge-m3'
               OR embedding_model_version IS NULL)
    ) OR EXISTS (
        SELECT 1 FROM lexpro.case_recommendation
        WHERE query_fact_embedding IS NOT NULL
          AND (model_name IS DISTINCT FROM 'BAAI/bge-m3' OR model_version IS NULL)
    ) THEN
        RAISE EXCEPTION 'Existing retrieval vectors must be 1024-dimensional BAAI/bge-m3 vectors before V4';
    END IF;
END
$$;

ALTER TABLE lexpro.typical_case
    ALTER COLUMN embedding TYPE public.vector(1024)
    USING embedding::public.vector(1024);

ALTER TABLE lexpro.case_recommendation
    ALTER COLUMN query_fact_embedding TYPE public.vector(1024)
    USING query_fact_embedding::public.vector(1024);

CREATE INDEX ix_typical_case_embedding_hnsw
    ON lexpro.typical_case
    USING hnsw (embedding public.vector_cosine_ops)
    WHERE embedding IS NOT NULL;

COMMENT ON COLUMN lexpro.typical_case.embedding IS
    'BAAI/bge-m3 1024-dimensional normalized embedding; cosine distance';
COMMENT ON COLUMN lexpro.case_recommendation.query_fact_embedding IS
    'BAAI/bge-m3 1024-dimensional normalized query embedding; cosine distance';
