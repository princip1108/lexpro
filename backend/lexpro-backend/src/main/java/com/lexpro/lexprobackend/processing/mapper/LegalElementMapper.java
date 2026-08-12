package com.lexpro.lexprobackend.processing.mapper;

import com.lexpro.lexprobackend.processing.domain.LegalElementJobRecord;
import com.lexpro.lexprobackend.processing.domain.LegalElementResult;
import com.lexpro.lexprobackend.processing.domain.LegalElementSource;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface LegalElementMapper {

    @Select("""
            SELECT d.doc_id, d.case_id, d.dossier_id, d.parse_status, d.raw_text, c.case_cause
            FROM lexpro.document_parse_result d
            JOIN lexpro.case_record c ON c.case_id = d.case_id
            WHERE d.case_id = #{caseId} AND d.doc_id = #{docId}
            FOR UPDATE OF d
            """)
    LegalElementSource lockSource(@Param("caseId") long caseId, @Param("docId") long docId);

    @Select("""
            SELECT d.doc_id, d.case_id, d.dossier_id, d.parse_status, d.raw_text, c.case_cause
            FROM lexpro.document_parse_result d
            JOIN lexpro.case_record c ON c.case_id = d.case_id
            WHERE d.case_id = #{caseId} AND d.doc_id = #{docId}
            """)
    LegalElementSource selectSource(@Param("caseId") long caseId, @Param("docId") long docId);

    @Insert("""
            INSERT INTO lexpro.legal_element_result (
                doc_id, case_id, case_cause, raw_elements_json, validation_report_json,
                model_name, model_version, prompt_version, schema_version, created_by,
                prompt_snapshot, generation_parameters_json, token_usage_json, request_id, duration_ms
            ) VALUES (
                #{docId}, #{caseId}, #{caseCause}, CAST(#{rawElementsJson} AS jsonb),
                CAST(#{validationReportJson} AS jsonb), #{modelName}, #{modelVersion},
                #{promptVersion}, #{schemaVersion}, #{createdBy}, #{promptSnapshot},
                CAST(#{generationParametersJson} AS jsonb), CAST(#{tokenUsageJson} AS jsonb),
                #{requestId}, #{durationMs}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "elementResultId", keyColumn = "element_result_id")
    int insert(LegalElementResult result);

    @Select("""
            SELECT element_result_id, doc_id, case_id, case_cause,
                   raw_elements_json::text AS raw_elements_json,
                   final_elements_json::text AS final_elements_json,
                   validation_report_json::text AS validation_report_json,
                   model_name, model_version, prompt_version, schema_version,
                   generation_parameters_json::text AS generation_parameters_json,
                   token_usage_json::text AS token_usage_json, request_id, duration_ms,
                   created_by, created_at, confirmed_by, confirmed_at
            FROM lexpro.legal_element_result
            WHERE case_id = #{caseId} AND doc_id = #{docId}
            ORDER BY created_at DESC, element_result_id DESC
            """)
    List<LegalElementResult> selectHistory(@Param("caseId") long caseId, @Param("docId") long docId);

    @Select("""
            SELECT element_result_id, doc_id, case_id, case_cause,
                   raw_elements_json::text AS raw_elements_json,
                   final_elements_json::text AS final_elements_json,
                   validation_report_json::text AS validation_report_json,
                   model_name, model_version, prompt_version, schema_version,
                   generation_parameters_json::text AS generation_parameters_json,
                   token_usage_json::text AS token_usage_json, request_id, duration_ms,
                   created_by, created_at, confirmed_by, confirmed_at
            FROM lexpro.legal_element_result
            WHERE case_id = #{caseId} AND doc_id = #{docId} AND element_result_id = #{elementResultId}
            """)
    LegalElementResult selectDetail(@Param("caseId") long caseId, @Param("docId") long docId,
                                    @Param("elementResultId") long elementResultId);

    @Update("""
            UPDATE lexpro.legal_element_result
            SET final_elements_json = CAST(#{finalElementsJson} AS jsonb),
                confirmed_by = #{confirmedBy}, confirmed_at = CURRENT_TIMESTAMP
            WHERE case_id = #{caseId} AND doc_id = #{docId} AND element_result_id = #{elementResultId}
              AND confirmed_at IS NULL
            """)
    int confirm(@Param("caseId") long caseId, @Param("docId") long docId,
                @Param("elementResultId") long elementResultId,
                @Param("finalElementsJson") String finalElementsJson, @Param("confirmedBy") long confirmedBy);

    @Select("""
            SELECT started.request_id,
                   CAST(started.detail ->> 'docId' AS bigint) AS doc_id,
                   CASE latest.operation_type
                       WHEN 'LEGAL_ELEMENTS_SUCCEEDED' THEN 'SUCCESS'
                       WHEN 'LEGAL_ELEMENTS_STARTED' THEN 'PROCESSING'
                       ELSE 'FAILED'
                   END AS job_status,
                   ler.element_result_id,
                   latest.detail ->> 'errorCode' AS error_code,
                   started.operation_time AS requested_at,
                   CASE WHEN latest.operation_type = 'LEGAL_ELEMENTS_STARTED'
                       THEN NULL ELSE latest.operation_time END AS completed_at
            FROM lexpro.operation_log started
            JOIN LATERAL (
                SELECT operation_type, operation_time, detail
                FROM lexpro.operation_log
                WHERE request_id = started.request_id AND case_id = started.case_id
                  AND operation_type IN (
                      'LEGAL_ELEMENTS_STARTED', 'LEGAL_ELEMENTS_SUCCEEDED',
                      'LEGAL_ELEMENTS_FAILED', 'LEGAL_ELEMENTS_REJECTED'
                  )
                ORDER BY log_id DESC
                LIMIT 1
            ) latest ON true
            LEFT JOIN lexpro.legal_element_result ler
              ON ler.case_id = started.case_id AND ler.request_id = started.request_id
            WHERE started.case_id = #{caseId} AND started.request_id = #{requestId}
              AND started.operation_type = 'LEGAL_ELEMENTS_STARTED'
            ORDER BY started.log_id DESC
            LIMIT 1
            """)
    LegalElementJobRecord selectJob(@Param("caseId") long caseId, @Param("requestId") String requestId);
}
