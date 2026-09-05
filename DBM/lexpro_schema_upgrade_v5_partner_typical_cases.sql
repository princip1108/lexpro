-- LexPro V5: add partner typical-case metadata and preserve separate score semantics.
-- Prerequisite: V1/V2/V3/V4 have already been applied in order.

ALTER TABLE lexpro.typical_case
    ADD COLUMN case_number varchar(100),
    ADD COLUMN region varchar(100);

ALTER TABLE lexpro.case_recommendation_item
    ADD COLUMN ranking_score numeric(12, 6),
    ADD CONSTRAINT ck_case_recommendation_item_ranking_score
        CHECK (ranking_score IS NULL OR (ranking_score >= -1 AND ranking_score <= 2));

CREATE INDEX ix_typical_case_case_number
    ON lexpro.typical_case (case_number);

CREATE INDEX ix_typical_case_region
    ON lexpro.typical_case (region);

COMMENT ON COLUMN lexpro.typical_case.case_number IS
    'Provider or source-system case number; not a LexPro primary key';
COMMENT ON COLUMN lexpro.typical_case.region IS
    'Normalized court or source region supplied by the case provider';
COMMENT ON COLUMN lexpro.case_recommendation_item.ranking_score IS
    'Final provider ranking score; similarity_score remains the fact similarity component';
