-- Derived from DBM/lexpro_schema_upgrade_v6_legal_llm_typical_case_metadata.sql for Flyway.
-- Source SHA-256: 6B8AA679765B60C77BA18A220FD84D60AF6F47C0BBD400CB2029AD918B59AEE0

ALTER TABLE lexpro.typical_case
    ADD COLUMN source_name varchar(255),
    ADD COLUMN source_file varchar(255),
    ADD COLUMN source_url varchar(2048),
    ADD COLUMN keywords_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT ck_typical_case_keywords_array
        CHECK (jsonb_typeof(keywords_json) = 'array');

ALTER TABLE lexpro.typical_case_content
    ADD COLUMN summary text,
    ADD COLUMN prosecutorial_process text,
    ADD COLUMN adjudication_result text,
    ADD COLUMN reasoning text,
    ADD COLUMN guiding_significance text;

CREATE INDEX ix_typical_case_source_name ON lexpro.typical_case (source_name);
CREATE INDEX ix_typical_case_case_level ON lexpro.typical_case (case_level);
CREATE INDEX ix_typical_case_court ON lexpro.typical_case (court);
CREATE INDEX ix_typical_case_doc_type ON lexpro.typical_case (doc_type);

COMMENT ON COLUMN lexpro.typical_case.source_name IS 'Original corpus/provider name; never an absolute local path.';
COMMENT ON COLUMN lexpro.typical_case.source_file IS 'Original source file base name only.';
COMMENT ON COLUMN lexpro.typical_case.source_url IS 'Validated original public source URL when available.';
COMMENT ON COLUMN lexpro.typical_case.keywords_json IS 'Normalized keyword string array.';
COMMENT ON COLUMN lexpro.typical_case_content.summary IS 'Source-provided case summary.';
COMMENT ON COLUMN lexpro.typical_case_content.prosecutorial_process IS 'Source-provided prosecutorial process.';
COMMENT ON COLUMN lexpro.typical_case_content.adjudication_result IS 'Source-provided adjudication result.';
COMMENT ON COLUMN lexpro.typical_case_content.reasoning IS 'Source-provided adjudication reasoning.';
COMMENT ON COLUMN lexpro.typical_case_content.guiding_significance IS 'Source-provided guiding significance.';
