package com.lexpro.lexprobackend.processing.mapper;

import com.lexpro.lexprobackend.processing.domain.EntityRecognitionJobRecord;
import com.lexpro.lexprobackend.processing.domain.EntityRecognitionResult;
import com.lexpro.lexprobackend.processing.domain.EntityRecognitionSource;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EntityRecognitionMapper {

    @Select("""
            SELECT doc_id, case_id, dossier_id, parse_status, raw_text,
                   parsed_text_json::text AS parsed_text_json
            FROM lexpro.document_parse_result
            WHERE case_id = #{caseId} AND doc_id = #{docId}
            FOR UPDATE
            """)
    EntityRecognitionSource lockSource(@Param("caseId") long caseId, @Param("docId") long docId);

    @Select("""
            SELECT doc_id, case_id, dossier_id, parse_status, raw_text,
                   parsed_text_json::text AS parsed_text_json
            FROM lexpro.document_parse_result
            WHERE case_id = #{caseId} AND doc_id = #{docId}
            """)
    EntityRecognitionSource selectSource(@Param("caseId") long caseId, @Param("docId") long docId);

    @Insert("""
            INSERT INTO lexpro.entity_result (
                doc_id, case_id, entities_json, model_name, model_version, prompt_version,
                schema_version, created_by, prompt_snapshot, generation_parameters_json,
                token_usage_json, request_id, duration_ms
            ) VALUES (
                #{docId}, #{caseId}, CAST(#{entitiesJson} AS jsonb), #{modelName}, #{modelVersion},
                #{promptVersion}, #{schemaVersion}, #{createdBy}, #{promptSnapshot},
                CAST(#{generationParametersJson} AS jsonb), CAST(#{tokenUsageJson} AS jsonb),
                #{requestId}, #{durationMs}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "entityResultId", keyColumn = "entity_result_id")
    int insert(EntityRecognitionResult result);

    @Select("""
            SELECT entity_result_id, doc_id, case_id, entities_json::text AS entities_json,
                   final_entities_json::text AS final_entities_json, model_name, model_version,
                   prompt_version, schema_version, generation_parameters_json::text AS generation_parameters_json,
                   token_usage_json::text AS token_usage_json, request_id, duration_ms, created_by, created_at,
                   confirmed_by, confirmed_at
            FROM lexpro.entity_result
            WHERE case_id = #{caseId} AND doc_id = #{docId}
            ORDER BY created_at DESC, entity_result_id DESC
            """)
    List<EntityRecognitionResult> selectHistory(@Param("caseId") long caseId, @Param("docId") long docId);

    @Select("""
            SELECT entity_result_id, doc_id, case_id, entities_json::text AS entities_json,
                   final_entities_json::text AS final_entities_json, model_name, model_version,
                   prompt_version, schema_version, generation_parameters_json::text AS generation_parameters_json,
                   token_usage_json::text AS token_usage_json, request_id, duration_ms, created_by, created_at,
                   confirmed_by, confirmed_at
            FROM lexpro.entity_result
            WHERE case_id = #{caseId} AND doc_id = #{docId} AND entity_result_id = #{entityResultId}
            """)
    EntityRecognitionResult selectDetail(@Param("caseId") long caseId, @Param("docId") long docId,
                                          @Param("entityResultId") long entityResultId);

    @Update("""
            UPDATE lexpro.entity_result
            SET final_entities_json = CAST(#{finalEntitiesJson} AS jsonb),
                confirmed_by = #{confirmedBy}, confirmed_at = CURRENT_TIMESTAMP
            WHERE case_id = #{caseId} AND doc_id = #{docId} AND entity_result_id = #{entityResultId}
              AND confirmed_at IS NULL
            """)
    int confirm(@Param("caseId") long caseId, @Param("docId") long docId,
                @Param("entityResultId") long entityResultId,
                @Param("finalEntitiesJson") String finalEntitiesJson, @Param("confirmedBy") long confirmedBy);

    @Select("""
            SELECT started.request_id,
                   CAST(started.detail ->> 'docId' AS bigint) AS doc_id,
                   CASE latest.operation_type
                       WHEN 'ENTITY_RECOGNITION_SUCCEEDED' THEN 'SUCCESS'
                       WHEN 'ENTITY_RECOGNITION_STARTED' THEN 'PROCESSING'
                       ELSE 'FAILED'
                   END AS job_status,
                   er.entity_result_id,
                   latest.detail ->> 'errorCode' AS error_code,
                   started.operation_time AS requested_at,
                   CASE WHEN latest.operation_type = 'ENTITY_RECOGNITION_STARTED'
                       THEN NULL ELSE latest.operation_time END AS completed_at
            FROM lexpro.operation_log started
            JOIN LATERAL (
                SELECT operation_type, operation_time, detail
                FROM lexpro.operation_log
                WHERE request_id = started.request_id AND case_id = started.case_id
                  AND operation_type IN (
                      'ENTITY_RECOGNITION_STARTED', 'ENTITY_RECOGNITION_SUCCEEDED',
                      'ENTITY_RECOGNITION_FAILED', 'ENTITY_RECOGNITION_REJECTED'
                  )
                ORDER BY log_id DESC
                LIMIT 1
            ) latest ON true
            LEFT JOIN lexpro.entity_result er
              ON er.case_id = started.case_id AND er.request_id = started.request_id
            WHERE started.case_id = #{caseId} AND started.request_id = #{requestId}
              AND started.operation_type = 'ENTITY_RECOGNITION_STARTED'
            ORDER BY started.log_id DESC
            LIMIT 1
            """)
    EntityRecognitionJobRecord selectJob(@Param("caseId") long caseId, @Param("requestId") String requestId);
}
