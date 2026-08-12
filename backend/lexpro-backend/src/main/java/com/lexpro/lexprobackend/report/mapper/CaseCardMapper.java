package com.lexpro.lexprobackend.report.mapper;

import com.lexpro.lexprobackend.report.domain.CaseCardField;
import com.lexpro.lexprobackend.report.domain.CaseCardFillTask;
import com.lexpro.lexprobackend.report.domain.CaseCardJobRecord;
import com.lexpro.lexprobackend.report.domain.CaseCardSourceMaterial;
import com.lexpro.lexprobackend.report.domain.CaseCardTaskSource;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CaseCardMapper {

    @Select("SELECT case_id FROM lexpro.case_record WHERE case_id = #{caseId} FOR UPDATE")
    Long lockCase(@Param("caseId") long caseId);

    @Select("""
            WITH available_source AS (
                SELECT 'DOCUMENT'::text AS source_type, d.doc_id AS source_id, d.dossier_id,
                       d.raw_text AS content,
                       (d.parse_status = 'SUCCESS' AND nullif(btrim(d.raw_text), '') IS NOT NULL) AS ready
                FROM lexpro.document_parse_result d WHERE d.case_id = #{caseId}
                UNION ALL
                SELECT 'ENTITY', e.entity_result_id, d.dossier_id,
                       COALESCE(e.final_entities_json, e.entities_json)::text,
                       (d.parse_status = 'SUCCESS' AND COALESCE(e.final_entities_json, e.entities_json) IS NOT NULL)
                FROM lexpro.entity_result e
                JOIN lexpro.document_parse_result d ON d.doc_id = e.doc_id AND d.case_id = e.case_id
                WHERE e.case_id = #{caseId}
                UNION ALL
                SELECT 'LEGAL_ELEMENT', l.element_result_id, d.dossier_id,
                       COALESCE(l.final_elements_json, l.raw_elements_json)::text,
                       (d.parse_status = 'SUCCESS' AND COALESCE(l.final_elements_json, l.raw_elements_json) IS NOT NULL)
                FROM lexpro.legal_element_result l
                JOIN lexpro.document_parse_result d ON d.doc_id = l.doc_id AND d.case_id = l.case_id
                WHERE l.case_id = #{caseId}
                UNION ALL
                SELECT 'SUMMARY', s.summary_id, NULL::bigint, s.summary_text,
                       (nullif(btrim(s.summary_text), '') IS NOT NULL)
                FROM lexpro.case_summary s WHERE s.case_id = #{caseId}
            )
            SELECT source_type, source_id, dossier_id, content, ready
            FROM available_source
            WHERE source_type = #{sourceType} AND source_id = #{sourceId}
            """)
    CaseCardSourceMaterial selectSource(@Param("caseId") long caseId,
                                        @Param("sourceType") String sourceType,
                                        @Param("sourceId") long sourceId);

    @Insert("""
            INSERT INTO lexpro.case_card_fill_task (
                case_id, fill_mode, fill_status, operator_id, model_name, prompt_version,
                generation_parameters_json, request_id, started_at
            ) VALUES (
                #{caseId}, #{fillMode}, 'PROCESSING', #{operatorId}, #{modelName}, #{promptVersion},
                '{}'::jsonb, #{requestId}, CURRENT_TIMESTAMP
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "fillTaskId", keyColumn = "fill_task_id")
    int insertTask(CaseCardFillTask task);

    @Insert("""
            INSERT INTO lexpro.case_card_task_source (
                fill_task_id, case_id, doc_id, entity_result_id, element_result_id, summary_id
            ) VALUES (
                #{fillTaskId}, #{caseId},
                CASE WHEN #{sourceType} = 'DOCUMENT' THEN #{sourceResultId} END,
                CASE WHEN #{sourceType} = 'ENTITY' THEN #{sourceResultId} END,
                CASE WHEN #{sourceType} = 'LEGAL_ELEMENT' THEN #{sourceResultId} END,
                CASE WHEN #{sourceType} = 'SUMMARY' THEN #{sourceResultId} END
            )
            """)
    int insertSource(@Param("fillTaskId") long fillTaskId, @Param("caseId") long caseId,
                     @Param("sourceType") String sourceType, @Param("sourceResultId") long sourceResultId);

    @Select("""
            SELECT fill_task_id, case_id, fill_mode, fill_status, operator_id, created_at,
                   confirmed_at, confirmed_by, model_name, model_version, prompt_version, prompt_snapshot,
                   generation_parameters_json::text AS generation_parameters_json,
                   token_usage_json::text AS token_usage_json, request_id, duration_ms,
                   started_at, completed_at, error_message
            FROM lexpro.case_card_fill_task
            WHERE case_id = #{caseId}
            ORDER BY created_at DESC, fill_task_id DESC
            """)
    List<CaseCardFillTask> selectHistory(@Param("caseId") long caseId);

    @Select("""
            SELECT fill_task_id, case_id, fill_mode, fill_status, operator_id, created_at,
                   confirmed_at, confirmed_by, model_name, model_version, prompt_version, prompt_snapshot,
                   generation_parameters_json::text AS generation_parameters_json,
                   token_usage_json::text AS token_usage_json, request_id, duration_ms,
                   started_at, completed_at, error_message
            FROM lexpro.case_card_fill_task
            WHERE case_id = #{caseId} AND fill_task_id = #{fillTaskId}
            """)
    CaseCardFillTask selectTask(@Param("caseId") long caseId, @Param("fillTaskId") long fillTaskId);

    @Select("""
            SELECT source_id,
                   CASE WHEN doc_id IS NOT NULL THEN 'DOCUMENT'
                        WHEN entity_result_id IS NOT NULL THEN 'ENTITY'
                        WHEN element_result_id IS NOT NULL THEN 'LEGAL_ELEMENT'
                        ELSE 'SUMMARY' END AS source_type,
                   COALESCE(doc_id, entity_result_id, element_result_id, summary_id) AS source_result_id,
                   created_at
            FROM lexpro.case_card_task_source
            WHERE case_id = #{caseId} AND fill_task_id = #{fillTaskId}
            ORDER BY source_id
            """)
    List<CaseCardTaskSource> selectTaskSources(@Param("caseId") long caseId,
                                               @Param("fillTaskId") long fillTaskId);

    @Select("""
            SELECT field_id, fill_task_id, field_code, field_name, field_value,
                   field_value_json::text AS field_value_json, source_text, source_file_id,
                   source_location_json::text AS source_location_json, confidence,
                   confirm_status, confirmed_by, confirmed_at
            FROM lexpro.case_card_field
            WHERE fill_task_id = #{fillTaskId}
            ORDER BY field_id
            """)
    List<CaseCardField> selectFields(@Param("fillTaskId") long fillTaskId);

    @Select("""
            SELECT f.field_id, f.fill_task_id, f.field_code, f.field_name, f.field_value,
                   f.field_value_json::text AS field_value_json, f.source_text, f.source_file_id,
                   f.source_location_json::text AS source_location_json, f.confidence,
                   f.confirm_status, f.confirmed_by, f.confirmed_at
            FROM lexpro.case_card_field f
            JOIN lexpro.case_card_fill_task t ON t.fill_task_id = f.fill_task_id
            WHERE t.case_id = #{caseId} AND t.fill_task_id = #{fillTaskId} AND f.field_id = #{fieldId}
            """)
    CaseCardField selectField(@Param("caseId") long caseId, @Param("fillTaskId") long fillTaskId,
                              @Param("fieldId") long fieldId);

    @Insert("""
            INSERT INTO lexpro.case_card_field (
                fill_task_id, field_code, field_name, field_value, field_value_json,
                source_text, source_file_id, source_location_json, confidence
            ) VALUES (
                #{fillTaskId}, #{fieldCode}, #{fieldName}, #{fieldValue},
                CAST(#{fieldValueJson} AS jsonb), #{sourceText}, #{sourceFileId},
                CAST(#{sourceLocationJson} AS jsonb), #{confidence}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "fieldId", keyColumn = "field_id")
    int insertField(CaseCardField field);

    @Update("""
            UPDATE lexpro.case_card_field
            SET field_value = #{field.fieldValue}, field_value_json = CAST(#{field.fieldValueJson} AS jsonb),
                confirm_status = #{field.confirmStatus}, confirmed_by = #{field.confirmedBy},
                confirmed_at = CURRENT_TIMESTAMP
            WHERE field_id = #{field.fieldId} AND fill_task_id = #{field.fillTaskId}
              AND confirm_status = 'UNCONFIRMED'
              AND EXISTS (
                  SELECT 1 FROM lexpro.case_card_fill_task
                  WHERE fill_task_id = #{field.fillTaskId} AND case_id = #{caseId} AND fill_status = 'DRAFT'
              )
            """)
    int confirmField(@Param("caseId") long caseId, @Param("field") CaseCardField field);

    @Select("""
            SELECT count(*) FROM lexpro.case_card_field
            WHERE fill_task_id = #{fillTaskId} AND confirm_status = 'UNCONFIRMED'
            """)
    int countUnconfirmedFields(@Param("fillTaskId") long fillTaskId);

    @Update("""
            UPDATE lexpro.case_card_fill_task
            SET fill_status = 'CONFIRMED', confirmed_by = #{userId}, confirmed_at = CURRENT_TIMESTAMP,
                completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP)
            WHERE case_id = #{caseId} AND fill_task_id = #{fillTaskId} AND fill_status = 'DRAFT'
            """)
    int confirmTask(@Param("caseId") long caseId, @Param("fillTaskId") long fillTaskId,
                    @Param("userId") long userId);

    @Update("""
            UPDATE lexpro.case_card_fill_task
            SET fill_status = 'DRAFT', model_version = #{modelVersion}, prompt_snapshot = #{promptSnapshot},
                generation_parameters_json = CAST(#{generationParametersJson} AS jsonb),
                token_usage_json = CAST(#{tokenUsageJson} AS jsonb), duration_ms = #{durationMs},
                completed_at = CURRENT_TIMESTAMP
            WHERE case_id = #{caseId} AND fill_task_id = #{fillTaskId} AND fill_status = 'PROCESSING'
            """)
    int completeTask(CaseCardFillTask task);

    @Update("""
            UPDATE lexpro.case_card_fill_task
            SET fill_status = 'FAILED', error_message = #{errorCode}, duration_ms = #{durationMs},
                completed_at = CURRENT_TIMESTAMP
            WHERE case_id = #{caseId} AND fill_task_id = #{fillTaskId} AND fill_status = 'PROCESSING'
            """)
    int failTask(@Param("caseId") long caseId, @Param("fillTaskId") long fillTaskId,
                 @Param("errorCode") String errorCode, @Param("durationMs") int durationMs);

    @Select("""
            SELECT request_id, fill_task_id, fill_mode,
                   CASE WHEN fill_status IN ('PENDING', 'PROCESSING') THEN 'PROCESSING'
                        WHEN fill_status = 'FAILED' THEN 'FAILED' ELSE 'SUCCESS' END AS job_status,
                   CASE WHEN fill_status = 'FAILED' THEN error_message END AS error_code,
                   created_at AS requested_at,
                   CASE WHEN fill_status IN ('PENDING', 'PROCESSING') THEN NULL ELSE completed_at END AS completed_at
            FROM lexpro.case_card_fill_task
            WHERE case_id = #{caseId} AND request_id = #{requestId}
            """)
    CaseCardJobRecord selectJob(@Param("caseId") long caseId, @Param("requestId") String requestId);
}
