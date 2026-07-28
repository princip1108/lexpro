-- LexPro schema upgrade V2: minimal core corrections
-- Prerequisite: lexpro_schema_postgresql.sql
-- This migration adds five tables:
--   dossier_folder, file_tag, evidence_file_tag,
--   typical_case_favorite, report_typical_case_reference
-- Run once:
--   psql -v ON_ERROR_STOP=1 -d lexpro -f DBM/lexpro_schema_upgrade_v2_core.sql

BEGIN;

SET LOCAL search_path TO lexpro, public;

DO $$
BEGIN
    IF to_regclass('lexpro.app_user') IS NULL
       OR to_regclass('lexpro.case_record') IS NULL
       OR to_regclass('lexpro.operation_log') IS NULL THEN
        RAISE EXCEPTION 'LexPro V1 baseline schema is not installed';
    END IF;
END;
$$;

-- -----------------------------------------------------------------------------
-- 1. Case workflow and assignment consistency
-- -----------------------------------------------------------------------------

ALTER TABLE case_record
    ADD COLUMN case_source varchar(100),
    ADD COLUMN current_stage varchar(50),
    ADD COLUMN deadline_at timestamptz;

UPDATE case_record
SET deadline_at = (deadline_date::timestamp + interval '1 day' - interval '1 second')
                  AT TIME ZONE 'Asia/Shanghai'
WHERE deadline_date IS NOT NULL;

ALTER TABLE case_record
    ADD CONSTRAINT ck_case_record_deadline_at
        CHECK (
            deadline_at IS NULL
            OR accept_date IS NULL
            OR deadline_at >= (accept_date::timestamp AT TIME ZONE 'Asia/Shanghai')
        );

COMMENT ON COLUMN case_record.deadline_date IS
    'Legacy date-only deadline. New code uses deadline_at.';
COMMENT ON COLUMN case_record.prosecutor_id IS
    'Legacy compatibility field. case_assignment is authoritative.';

ALTER TABLE case_assignment
    ADD COLUMN assigned_by bigint REFERENCES app_user(user_id) ON DELETE SET NULL;

UPDATE case_assignment ca
SET assigned_by = cr.creator_id
FROM case_record cr
WHERE cr.case_id = ca.case_id;

INSERT INTO case_assignment (
    case_id, user_id, assignment_role, is_current, assigned_at, assigned_by
)
SELECT
    cr.case_id,
    cr.prosecutor_id,
    'PROSECUTOR',
    true,
    cr.created_at,
    cr.creator_id
FROM case_record cr
WHERE cr.prosecutor_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM case_assignment ca
      WHERE ca.case_id = cr.case_id
        AND ca.user_id = cr.prosecutor_id
        AND ca.assignment_role = 'PROSECUTOR'
        AND ca.is_current
  );

UPDATE case_assignment
SET ended_at = assigned_at
WHERE NOT is_current AND ended_at IS NULL;

ALTER TABLE case_assignment
    DROP CONSTRAINT ck_case_assignment_current_end,
    ADD CONSTRAINT ck_case_assignment_current_end
        CHECK (is_current = (ended_at IS NULL));

CREATE INDEX ix_case_record_deadline_at ON case_record (deadline_at);
CREATE INDEX ix_case_record_stage ON case_record (current_stage);
CREATE INDEX ix_case_assignment_current_case_role
    ON case_assignment (case_id, assignment_role)
    WHERE is_current;

-- -----------------------------------------------------------------------------
-- 2. Sensitive data and file lifecycle
-- -----------------------------------------------------------------------------

ALTER TABLE case_party
    ADD COLUMN birth_date date,
    ADD COLUMN identity_number_hash varchar(128),
    ADD COLUMN identity_number_masked varchar(100);

COMMENT ON COLUMN case_party.age IS
    'Legacy snapshot. Prefer birth_date.';
COMMENT ON COLUMN case_party.identity_number IS
    'Ciphertext only; plaintext identity numbers are forbidden.';
COMMENT ON COLUMN case_party.identity_number_hash IS
    'Keyed lookup hash; do not use an unsalted plain hash.';

ALTER TABLE evidence_file
    ADD COLUMN file_status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN deleted_at timestamptz,
    ADD COLUMN deleted_by bigint REFERENCES app_user(user_id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_evidence_file_status
        CHECK (file_status IN ('ACTIVE', 'DELETED')),
    ADD CONSTRAINT ck_evidence_file_deleted
        CHECK ((file_status = 'DELETED') = (deleted_at IS NOT NULL));

CREATE TRIGGER trg_evidence_file_updated_at
BEFORE UPDATE ON evidence_file
FOR EACH ROW EXECUTE FUNCTION lexpro.set_updated_at();

CREATE INDEX ix_evidence_file_status ON evidence_file (file_status);

-- -----------------------------------------------------------------------------
-- 3. Case ownership columns and composite foreign keys
--
-- The repeated case_id values are intentional foreign-key discriminators.
-- Composite FKs guarantee that related records cannot cross case boundaries.
-- -----------------------------------------------------------------------------

ALTER TABLE evidence_file
    ADD CONSTRAINT ux_evidence_file_id_case UNIQUE (dossier_id, case_id);

ALTER TABLE document_parse_result
    ADD COLUMN case_id bigint,
    ADD COLUMN requested_by bigint REFERENCES app_user(user_id) ON DELETE SET NULL,
    ADD COLUMN request_id varchar(100),
    ADD COLUMN parser_parameters_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN duration_ms integer,
    ADD COLUMN is_current boolean NOT NULL DEFAULT false;

UPDATE document_parse_result dpr
SET case_id = ef.case_id
FROM evidence_file ef
WHERE ef.dossier_id = dpr.dossier_id;

WITH ranked AS (
    SELECT
        doc_id,
        row_number() OVER (
            PARTITION BY dossier_id
            ORDER BY version_no DESC, doc_id DESC
        ) AS version_rank
    FROM document_parse_result
)
UPDATE document_parse_result dpr
SET is_current = (ranked.version_rank = 1)
FROM ranked
WHERE ranked.doc_id = dpr.doc_id;

ALTER TABLE document_parse_result
    ALTER COLUMN case_id SET NOT NULL,
    ADD CONSTRAINT ux_document_parse_result_id_case UNIQUE (doc_id, case_id),
    ADD CONSTRAINT fk_document_parse_result_file_case
        FOREIGN KEY (dossier_id, case_id)
        REFERENCES evidence_file(dossier_id, case_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT ck_document_parse_parameters_json
        CHECK (jsonb_typeof(parser_parameters_json) = 'object'),
    ADD CONSTRAINT ck_document_parse_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0);

CREATE UNIQUE INDEX ux_document_parse_result_current
    ON document_parse_result (dossier_id)
    WHERE is_current;

ALTER TABLE entity_result
    ADD COLUMN case_id bigint;

UPDATE entity_result er
SET case_id = dpr.case_id
FROM document_parse_result dpr
WHERE dpr.doc_id = er.doc_id;

ALTER TABLE entity_result
    ALTER COLUMN case_id SET NOT NULL,
    ADD CONSTRAINT ux_entity_result_id_case UNIQUE (entity_result_id, case_id),
    ADD CONSTRAINT fk_entity_result_doc_case
        FOREIGN KEY (doc_id, case_id)
        REFERENCES document_parse_result(doc_id, case_id)
        ON DELETE RESTRICT;

ALTER TABLE legal_element_result
    ADD COLUMN case_id bigint;

UPDATE legal_element_result ler
SET case_id = dpr.case_id
FROM document_parse_result dpr
WHERE dpr.doc_id = ler.doc_id;

ALTER TABLE legal_element_result
    ALTER COLUMN case_id SET NOT NULL,
    ADD CONSTRAINT ux_legal_element_result_id_case UNIQUE (element_result_id, case_id),
    ADD CONSTRAINT fk_legal_element_result_doc_case
        FOREIGN KEY (doc_id, case_id)
        REFERENCES document_parse_result(doc_id, case_id)
        ON DELETE RESTRICT;

ALTER TABLE case_summary
    ADD CONSTRAINT ux_case_summary_id_case UNIQUE (summary_id, case_id);

ALTER TABLE case_card_fill_task
    ADD CONSTRAINT ux_case_card_fill_task_id_case UNIQUE (fill_task_id, case_id);

ALTER TABLE case_card_task_source
    ADD COLUMN case_id bigint;

UPDATE case_card_task_source ccts
SET case_id = ccft.case_id
FROM case_card_fill_task ccft
WHERE ccft.fill_task_id = ccts.fill_task_id;

ALTER TABLE case_card_task_source
    ALTER COLUMN case_id SET NOT NULL,
    ADD CONSTRAINT fk_case_card_source_task_case
        FOREIGN KEY (fill_task_id, case_id)
        REFERENCES case_card_fill_task(fill_task_id, case_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_case_card_source_doc_case
        FOREIGN KEY (doc_id, case_id)
        REFERENCES document_parse_result(doc_id, case_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_case_card_source_entity_case
        FOREIGN KEY (entity_result_id, case_id)
        REFERENCES entity_result(entity_result_id, case_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_case_card_source_element_case
        FOREIGN KEY (element_result_id, case_id)
        REFERENCES legal_element_result(element_result_id, case_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_case_card_source_summary_case
        FOREIGN KEY (summary_id, case_id)
        REFERENCES case_summary(summary_id, case_id)
        ON DELETE RESTRICT;

ALTER TABLE case_report
    ADD CONSTRAINT ux_case_report_id_case UNIQUE (report_id, case_id),
    ADD CONSTRAINT fk_case_report_card_task_case
        FOREIGN KEY (card_fill_task_id, case_id)
        REFERENCES case_card_fill_task(fill_task_id, case_id)
        ON DELETE RESTRICT;

ALTER TABLE report_evidence
    ADD COLUMN case_id bigint;

UPDATE report_evidence re
SET case_id = cr.case_id
FROM case_report cr
WHERE cr.report_id = re.report_id;

ALTER TABLE report_evidence
    ALTER COLUMN case_id SET NOT NULL,
    ADD CONSTRAINT fk_report_evidence_report_case
        FOREIGN KEY (report_id, case_id)
        REFERENCES case_report(report_id, case_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_report_evidence_file_case
        FOREIGN KEY (dossier_id, case_id)
        REFERENCES evidence_file(dossier_id, case_id)
        ON DELETE RESTRICT;

ALTER TABLE report_legal_element_result
    ADD COLUMN case_id bigint;

UPDATE report_legal_element_result rler
SET case_id = cr.case_id
FROM case_report cr
WHERE cr.report_id = rler.report_id;

ALTER TABLE report_legal_element_result
    ALTER COLUMN case_id SET NOT NULL,
    ADD CONSTRAINT fk_report_element_report_case
        FOREIGN KEY (report_id, case_id)
        REFERENCES case_report(report_id, case_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_report_element_result_case
        FOREIGN KEY (element_result_id, case_id)
        REFERENCES legal_element_result(element_result_id, case_id)
        ON DELETE RESTRICT;

ALTER TABLE case_recommendation
    ADD CONSTRAINT ux_case_recommendation_id_case UNIQUE (recommend_id, case_id),
    ADD CONSTRAINT fk_case_recommendation_summary_case
        FOREIGN KEY (source_summary_id, case_id)
        REFERENCES case_summary(summary_id, case_id)
        ON DELETE RESTRICT;

ALTER TABLE case_recommendation_item
    ADD COLUMN case_id bigint;

UPDATE case_recommendation_item cri
SET case_id = cr.case_id
FROM case_recommendation cr
WHERE cr.recommend_id = cri.recommend_id;

ALTER TABLE case_recommendation_item
    ALTER COLUMN case_id SET NOT NULL,
    ADD CONSTRAINT ux_recommendation_item_id_case
        UNIQUE (item_id, case_id),
    ADD CONSTRAINT ux_recommendation_item_id_case_typical
        UNIQUE (item_id, case_id, typical_case_id),
    ADD CONSTRAINT fk_recommendation_item_recommendation_case
        FOREIGN KEY (recommend_id, case_id)
        REFERENCES case_recommendation(recommend_id, case_id)
        ON DELETE CASCADE;

CREATE INDEX ix_document_parse_result_case_id ON document_parse_result (case_id);
CREATE INDEX ix_entity_result_case_id ON entity_result (case_id);
CREATE INDEX ix_legal_element_result_case_id ON legal_element_result (case_id);
CREATE INDEX ix_case_card_task_source_case_id ON case_card_task_source (case_id);
CREATE INDEX ix_report_evidence_case_id ON report_evidence (case_id);
CREATE INDEX ix_report_element_case_id ON report_legal_element_result (case_id);

-- -----------------------------------------------------------------------------
-- 4. AI provenance stored with each generated artifact
-- -----------------------------------------------------------------------------

ALTER TABLE entity_result
    ADD COLUMN prompt_snapshot text,
    ADD COLUMN generation_parameters_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN token_usage_json jsonb,
    ADD COLUMN request_id varchar(100),
    ADD COLUMN duration_ms integer,
    ADD COLUMN final_entities_json jsonb,
    ADD COLUMN confirmed_by bigint REFERENCES app_user(user_id) ON DELETE SET NULL,
    ADD COLUMN confirmed_at timestamptz,
    ADD CONSTRAINT ck_entity_generation_parameters
        CHECK (jsonb_typeof(generation_parameters_json) = 'object'),
    ADD CONSTRAINT ck_entity_token_usage
        CHECK (token_usage_json IS NULL OR jsonb_typeof(token_usage_json) = 'object'),
    ADD CONSTRAINT ck_entity_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0),
    ADD CONSTRAINT ck_entity_final_json
        CHECK (final_entities_json IS NULL OR jsonb_typeof(final_entities_json) IN ('array', 'object')),
    ADD CONSTRAINT ck_entity_confirmed_actor
        CHECK (confirmed_by IS NULL OR confirmed_at IS NOT NULL),
    ADD CONSTRAINT ck_entity_confirmed_content
        CHECK (confirmed_at IS NULL OR final_entities_json IS NOT NULL);

ALTER TABLE legal_element_result
    ADD COLUMN prompt_snapshot text,
    ADD COLUMN generation_parameters_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN token_usage_json jsonb,
    ADD COLUMN request_id varchar(100),
    ADD COLUMN duration_ms integer,
    DROP CONSTRAINT ck_legal_element_result_confirmed,
    ADD CONSTRAINT ck_legal_element_result_confirmed
        CHECK (confirmed_by IS NULL OR confirmed_at IS NOT NULL),
    ADD CONSTRAINT ck_legal_generation_parameters
        CHECK (jsonb_typeof(generation_parameters_json) = 'object'),
    ADD CONSTRAINT ck_legal_token_usage
        CHECK (token_usage_json IS NULL OR jsonb_typeof(token_usage_json) = 'object'),
    ADD CONSTRAINT ck_legal_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0),
    ADD CONSTRAINT ck_legal_confirmed_content
        CHECK (confirmed_at IS NULL OR final_elements_json IS NOT NULL);

ALTER TABLE case_summary
    ADD COLUMN version_no integer,
    ADD COLUMN schema_version varchar(50),
    ADD COLUMN prompt_snapshot text,
    ADD COLUMN generation_parameters_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN token_usage_json jsonb,
    ADD COLUMN request_id varchar(100),
    ADD COLUMN duration_ms integer,
    ADD COLUMN confirmed_by bigint REFERENCES app_user(user_id) ON DELETE SET NULL,
    ADD COLUMN confirmed_at timestamptz;

WITH ranked AS (
    SELECT
        summary_id,
        row_number() OVER (
            PARTITION BY case_id, summary_type
            ORDER BY created_at, summary_id
        ) AS generated_version_no
    FROM case_summary
)
UPDATE case_summary cs
SET version_no = ranked.generated_version_no
FROM ranked
WHERE ranked.summary_id = cs.summary_id;

ALTER TABLE case_summary
    ALTER COLUMN version_no SET NOT NULL,
    ADD CONSTRAINT ck_case_summary_version CHECK (version_no > 0),
    ADD CONSTRAINT ux_case_summary_version
        UNIQUE (case_id, summary_type, version_no),
    ADD CONSTRAINT ck_case_summary_generation_parameters
        CHECK (jsonb_typeof(generation_parameters_json) = 'object'),
    ADD CONSTRAINT ck_case_summary_token_usage
        CHECK (token_usage_json IS NULL OR jsonb_typeof(token_usage_json) = 'object'),
    ADD CONSTRAINT ck_case_summary_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0),
    ADD CONSTRAINT ck_case_summary_confirmed
        CHECK (confirmed_by IS NULL OR confirmed_at IS NOT NULL);

ALTER TABLE case_card_fill_task
    ADD COLUMN model_name varchar(100),
    ADD COLUMN model_version varchar(100),
    ADD COLUMN prompt_version varchar(100),
    ADD COLUMN prompt_snapshot text,
    ADD COLUMN generation_parameters_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN token_usage_json jsonb,
    ADD COLUMN request_id varchar(100),
    ADD COLUMN duration_ms integer,
    ADD COLUMN started_at timestamptz,
    ADD COLUMN completed_at timestamptz,
    ADD COLUMN error_message text,
    DROP CONSTRAINT ck_case_card_fill_task_confirmed,
    ADD CONSTRAINT ck_case_card_fill_task_confirmed
        CHECK (confirmed_by IS NULL OR confirmed_at IS NOT NULL),
    ADD CONSTRAINT ck_case_card_generation_parameters
        CHECK (jsonb_typeof(generation_parameters_json) = 'object'),
    ADD CONSTRAINT ck_case_card_token_usage
        CHECK (token_usage_json IS NULL OR jsonb_typeof(token_usage_json) = 'object'),
    ADD CONSTRAINT ck_case_card_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0),
    ADD CONSTRAINT ck_case_card_time_order
        CHECK (
            (started_at IS NULL OR started_at >= created_at)
            AND (completed_at IS NULL OR completed_at >= created_at)
            AND (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
        );

UPDATE case_card_fill_task
SET error_message = 'Historical failed task without a recorded error',
    completed_at = COALESCE(completed_at, created_at)
WHERE fill_status = 'FAILED' AND error_message IS NULL;

UPDATE case_card_fill_task
SET completed_at = COALESCE(completed_at, confirmed_at, created_at)
WHERE fill_status = 'CONFIRMED';

ALTER TABLE case_card_fill_task
    ADD CONSTRAINT ck_case_card_failed_error
        CHECK (fill_status <> 'FAILED' OR error_message IS NOT NULL);

ALTER TABLE case_card_field
    DROP CONSTRAINT ck_case_card_field_value,
    DROP CONSTRAINT ck_case_card_field_confirmed,
    ADD CONSTRAINT ck_case_card_field_confirmed
        CHECK (confirmed_by IS NULL OR confirmed_at IS NOT NULL),
    ADD CONSTRAINT ck_case_card_field_confirmed_value
        CHECK (
            confirm_status <> 'CONFIRMED'
            OR field_value IS NOT NULL
            OR field_value_json IS NOT NULL
        );

-- -----------------------------------------------------------------------------
-- 5. Report and recommendation versions/provenance
-- -----------------------------------------------------------------------------

ALTER TABLE report_template
    ADD COLUMN template_code varchar(100),
    ADD COLUMN schema_version varchar(50) NOT NULL DEFAULT '1.0';

WITH template_codes AS (
    SELECT
        template_id,
        'TPL-' || min(template_id) OVER (
            PARTITION BY template_name, template_type
        ) AS generated_code
    FROM report_template
)
UPDATE report_template rt
SET template_code = tc.generated_code
FROM template_codes tc
WHERE tc.template_id = rt.template_id;

WITH active_versions AS (
    SELECT
        template_id,
        row_number() OVER (
            PARTITION BY template_code
            ORDER BY version_no DESC, template_id DESC
        ) AS active_rank
    FROM report_template
    WHERE status = 'ACTIVE'
)
UPDATE report_template rt
SET status = 'DISABLED'
FROM active_versions av
WHERE av.template_id = rt.template_id AND av.active_rank > 1;

ALTER TABLE report_template
    ALTER COLUMN template_code SET NOT NULL,
    ADD CONSTRAINT ux_report_template_code_version
        UNIQUE (template_code, version_no),
    ADD CONSTRAINT ck_report_template_content_json
        CHECK (jsonb_typeof(template_content_json) IN ('object', 'array'));

CREATE UNIQUE INDEX ux_report_template_active_code
    ON report_template (template_code)
    WHERE status = 'ACTIVE';

ALTER TABLE case_report
    ADD COLUMN is_current boolean NOT NULL DEFAULT false,
    ADD COLUMN created_at timestamptz,
    ADD COLUMN created_by bigint REFERENCES app_user(user_id) ON DELETE SET NULL,
    ADD COLUMN content_schema_version varchar(50) NOT NULL DEFAULT '1.0',
    ADD COLUMN model_name varchar(100),
    ADD COLUMN model_version varchar(100),
    ADD COLUMN prompt_version varchar(100),
    ADD COLUMN prompt_snapshot text,
    ADD COLUMN generation_parameters_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN token_usage_json jsonb,
    ADD COLUMN request_id varchar(100),
    ADD COLUMN duration_ms integer,
    ADD COLUMN lock_version integer NOT NULL DEFAULT 0;

UPDATE case_report
SET created_at = COALESCE(generated_at, updated_at, CURRENT_TIMESTAMP),
    created_by = operator_id;

WITH ranked AS (
    SELECT
        report_id,
        row_number() OVER (
            PARTITION BY case_id, report_type
            ORDER BY version_no DESC, report_id DESC
        ) AS version_rank
    FROM case_report
)
UPDATE case_report cr
SET is_current = (ranked.version_rank = 1)
FROM ranked
WHERE ranked.report_id = cr.report_id;

ALTER TABLE case_report
    ALTER COLUMN created_at SET NOT NULL,
    DROP CONSTRAINT ck_case_report_finalized,
    ADD CONSTRAINT ck_case_report_finalized
        CHECK (finalized_by IS NULL OR finalized_at IS NOT NULL),
    ADD CONSTRAINT ck_case_report_generation_parameters
        CHECK (jsonb_typeof(generation_parameters_json) = 'object'),
    ADD CONSTRAINT ck_case_report_token_usage
        CHECK (token_usage_json IS NULL OR jsonb_typeof(token_usage_json) = 'object'),
    ADD CONSTRAINT ck_case_report_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0),
    ADD CONSTRAINT ck_case_report_lock_version
        CHECK (lock_version >= 0),
    ADD CONSTRAINT ck_case_report_content_json
        CHECK (report_content_json IS NULL OR jsonb_typeof(report_content_json) IN ('object', 'array'));

CREATE UNIQUE INDEX ux_case_report_current
    ON case_report (case_id, report_type)
    WHERE is_current;

ALTER TABLE typical_case
    ADD COLUMN embedding_model_name varchar(100),
    ADD COLUMN embedding_model_version varchar(100),
    ADD COLUMN embedded_at timestamptz;

ALTER TABLE case_recommendation
    ADD COLUMN prompt_version varchar(100),
    ADD COLUMN prompt_snapshot text,
    ADD COLUMN query_parameters_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN token_usage_json jsonb,
    ADD COLUMN request_id varchar(100),
    ADD COLUMN duration_ms integer,
    ADD CONSTRAINT ck_case_recommendation_parameters
        CHECK (jsonb_typeof(query_parameters_json) = 'object'),
    ADD CONSTRAINT ck_case_recommendation_token_usage
        CHECK (token_usage_json IS NULL OR jsonb_typeof(token_usage_json) = 'object'),
    ADD CONSTRAINT ck_case_recommendation_duration
        CHECK (duration_ms IS NULL OR duration_ms >= 0);

-- -----------------------------------------------------------------------------
-- 6. Three required normalized tables
-- -----------------------------------------------------------------------------

CREATE TABLE dossier_folder (
    folder_id          bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    case_id            bigint NOT NULL REFERENCES case_record(case_id) ON DELETE RESTRICT,
    parent_folder_id   bigint,
    folder_name        varchar(255) NOT NULL,
    sort_no            integer,
    created_by         bigint REFERENCES app_user(user_id) ON DELETE SET NULL,
    created_at         timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_dossier_folder_id_case UNIQUE (folder_id, case_id),
    CONSTRAINT fk_dossier_folder_parent_case
        FOREIGN KEY (parent_folder_id, case_id)
        REFERENCES dossier_folder(folder_id, case_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_dossier_folder_name_not_blank CHECK (btrim(folder_name) <> ''),
    CONSTRAINT ck_dossier_folder_not_self
        CHECK (parent_folder_id IS NULL OR parent_folder_id <> folder_id),
    CONSTRAINT ck_dossier_folder_sort CHECK (sort_no IS NULL OR sort_no >= 0)
);

CREATE UNIQUE INDEX ux_dossier_folder_name
    ON dossier_folder (
        case_id,
        COALESCE(parent_folder_id, 0::bigint),
        lower(folder_name)
    );

ALTER TABLE evidence_file
    ADD COLUMN folder_id bigint,
    ADD CONSTRAINT fk_evidence_file_folder_case
        FOREIGN KEY (folder_id, case_id)
        REFERENCES dossier_folder(folder_id, case_id)
        ON DELETE RESTRICT;

CREATE INDEX ix_evidence_file_folder ON evidence_file (folder_id);

CREATE TABLE file_tag (
    tag_id              bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    case_id             bigint NOT NULL REFERENCES case_record(case_id) ON DELETE CASCADE,
    tag_name            varchar(100) NOT NULL,
    color               varchar(20),
    created_by          bigint REFERENCES app_user(user_id) ON DELETE SET NULL,
    created_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_file_tag_name_not_blank CHECK (btrim(tag_name) <> '')
);

CREATE UNIQUE INDEX ux_file_tag_name
    ON file_tag (case_id, lower(tag_name));

CREATE TABLE evidence_file_tag (
    dossier_id          bigint NOT NULL REFERENCES evidence_file(dossier_id) ON DELETE CASCADE,
    tag_id              bigint NOT NULL REFERENCES file_tag(tag_id) ON DELETE CASCADE,
    created_by          bigint REFERENCES app_user(user_id) ON DELETE SET NULL,
    created_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (dossier_id, tag_id)
);

CREATE INDEX ix_evidence_file_tag_tag ON evidence_file_tag (tag_id);

CREATE OR REPLACE FUNCTION lexpro.validate_evidence_file_tag_case()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    file_case_id bigint;
    tag_case_id bigint;
BEGIN
    SELECT case_id INTO file_case_id
    FROM evidence_file
    WHERE dossier_id = NEW.dossier_id;

    SELECT case_id INTO tag_case_id
    FROM file_tag
    WHERE tag_id = NEW.tag_id;

    IF file_case_id IS DISTINCT FROM tag_case_id THEN
        RAISE EXCEPTION
            'Evidence file % and tag % belong to different cases',
            NEW.dossier_id,
            NEW.tag_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_evidence_file_tag_case
BEFORE INSERT OR UPDATE ON evidence_file_tag
FOR EACH ROW EXECUTE FUNCTION lexpro.validate_evidence_file_tag_case();

CREATE TABLE typical_case_favorite (
    user_id             bigint NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    typical_case_id     bigint NOT NULL REFERENCES typical_case(typical_case_id) ON DELETE CASCADE,
    created_at          timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, typical_case_id)
);

CREATE INDEX ix_typical_case_favorite_case
    ON typical_case_favorite (typical_case_id);

CREATE TABLE report_typical_case_reference (
    report_id               bigint NOT NULL,
    case_id                 bigint NOT NULL,
    typical_case_id         bigint NOT NULL REFERENCES typical_case(typical_case_id) ON DELETE RESTRICT,
    recommendation_item_id  bigint,
    section_code            varchar(100),
    citation_note           text,
    sort_no                 integer,
    cited_by                bigint REFERENCES app_user(user_id) ON DELETE SET NULL,
    created_at              timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (report_id, typical_case_id),
    CONSTRAINT fk_report_typical_reference_report_case
        FOREIGN KEY (report_id, case_id)
        REFERENCES case_report(report_id, case_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_report_typical_reference_item
        FOREIGN KEY (recommendation_item_id, case_id, typical_case_id)
        REFERENCES case_recommendation_item(item_id, case_id, typical_case_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_report_typical_reference_sort
        CHECK (sort_no IS NULL OR sort_no > 0)
);

CREATE INDEX ix_report_typical_reference_case
    ON report_typical_case_reference (typical_case_id);

COMMENT ON TABLE dossier_folder IS 'Case-scoped electronic dossier hierarchy.';
COMMENT ON TABLE file_tag IS 'Normalized case-scoped dossier tags.';
COMMENT ON TABLE evidence_file_tag IS 'Many-to-many relation between dossier files and tags.';
COMMENT ON TABLE typical_case_favorite IS 'User-to-typical-case favorite relation.';
COMMENT ON TABLE report_typical_case_reference IS 'Typical cases cited by reports.';

-- Embedding dimensions remain unresolved. Add vector dimensions and an HNSW
-- index only after selecting the production embedding model.

COMMIT;
