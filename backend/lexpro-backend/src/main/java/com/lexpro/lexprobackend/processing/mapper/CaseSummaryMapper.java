package com.lexpro.lexprobackend.processing.mapper;

import com.lexpro.lexprobackend.processing.domain.CaseSummaryJobRecord;
import com.lexpro.lexprobackend.processing.domain.CaseSummaryResult;
import com.lexpro.lexprobackend.processing.domain.CaseSummarySource;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CaseSummaryMapper {

    @Select("SELECT case_id FROM lexpro.case_record WHERE case_id = #{caseId} FOR UPDATE")
    Long lockCase(@Param("caseId") long caseId);

    @Select({
            "<script>",
            "SELECT doc_id, parse_status, raw_text",
            "FROM lexpro.document_parse_result",
            "WHERE case_id = #{caseId} AND doc_id IN",
            "<foreach collection='docIds' item='docId' open='(' separator=',' close=')'>",
            "#{docId}",
            "</foreach>",
            "ORDER BY doc_id",
            "</script>"
    })
    List<CaseSummarySource> selectSources(@Param("caseId") long caseId,
                                          @Param("docIds") List<Long> docIds);

    @Select("""
            SELECT COALESCE(MAX(version_no), 0) + 1
            FROM lexpro.case_summary
            WHERE case_id = #{caseId} AND summary_type = #{summaryType}
            """)
    int selectNextVersion(@Param("caseId") long caseId, @Param("summaryType") String summaryType);

    @Update("""
            UPDATE lexpro.case_summary
            SET is_current = false
            WHERE case_id = #{caseId} AND summary_type = #{summaryType} AND is_current
            """)
    int clearCurrent(@Param("caseId") long caseId, @Param("summaryType") String summaryType);

    @Insert("""
            INSERT INTO lexpro.case_summary (
                case_id, summary_type, summary_text, version_no, model_name, model_version,
                prompt_version, schema_version, prompt_snapshot, generation_parameters_json,
                token_usage_json, request_id, duration_ms, is_current, created_by
            ) VALUES (
                #{caseId}, #{summaryType}, #{summaryText}, #{versionNo}, #{modelName}, #{modelVersion},
                #{promptVersion}, #{schemaVersion}, #{promptSnapshot},
                CAST(#{generationParametersJson} AS jsonb), CAST(#{tokenUsageJson} AS jsonb),
                #{requestId}, #{durationMs}, true, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "summaryId", keyColumn = "summary_id")
    int insert(CaseSummaryResult result);

    @Select("""
            SELECT summary_id, case_id, summary_type, summary_text, version_no,
                   model_name, model_version, prompt_version, schema_version,
                   generation_parameters_json::text AS generation_parameters_json,
                   token_usage_json::text AS token_usage_json, request_id, duration_ms,
                   is_current, created_by, created_at, confirmed_by, confirmed_at
            FROM lexpro.case_summary
            WHERE case_id = #{caseId} AND summary_type = #{summaryType}
            ORDER BY version_no DESC, summary_id DESC
            """)
    List<CaseSummaryResult> selectHistory(@Param("caseId") long caseId,
                                          @Param("summaryType") String summaryType);

    @Select("""
            SELECT summary_id, case_id, summary_type, summary_text, version_no,
                   model_name, model_version, prompt_version, schema_version,
                   generation_parameters_json::text AS generation_parameters_json,
                   token_usage_json::text AS token_usage_json, request_id, duration_ms,
                   is_current, created_by, created_at, confirmed_by, confirmed_at
            FROM lexpro.case_summary
            WHERE case_id = #{caseId} AND summary_id = #{summaryId}
            """)
    CaseSummaryResult selectDetail(@Param("caseId") long caseId, @Param("summaryId") long summaryId);

    @Update("""
            UPDATE lexpro.case_summary
            SET confirmed_by = #{confirmedBy}, confirmed_at = CURRENT_TIMESTAMP
            WHERE case_id = #{caseId} AND summary_id = #{summaryId} AND confirmed_at IS NULL
            """)
    int confirm(@Param("caseId") long caseId, @Param("summaryId") long summaryId,
                @Param("confirmedBy") long confirmedBy);

    @Select("""
            SELECT started.request_id,
                   started.detail ->> 'summaryType' AS summary_type,
                   CASE latest.operation_type
                       WHEN 'CASE_SUMMARY_SUCCEEDED' THEN 'SUCCESS'
                       WHEN 'CASE_SUMMARY_STARTED' THEN 'PROCESSING'
                       ELSE 'FAILED'
                   END AS job_status,
                   cs.summary_id,
                   latest.detail ->> 'errorCode' AS error_code,
                   started.operation_time AS requested_at,
                   CASE WHEN latest.operation_type = 'CASE_SUMMARY_STARTED'
                       THEN NULL ELSE latest.operation_time END AS completed_at
            FROM lexpro.operation_log started
            JOIN LATERAL (
                SELECT operation_type, operation_time, detail
                FROM lexpro.operation_log
                WHERE request_id = started.request_id AND case_id = started.case_id
                  AND operation_type IN (
                      'CASE_SUMMARY_STARTED', 'CASE_SUMMARY_SUCCEEDED',
                      'CASE_SUMMARY_FAILED', 'CASE_SUMMARY_REJECTED'
                  )
                ORDER BY log_id DESC
                LIMIT 1
            ) latest ON true
            LEFT JOIN lexpro.case_summary cs
              ON cs.case_id = started.case_id AND cs.request_id = started.request_id
            WHERE started.case_id = #{caseId} AND started.request_id = #{requestId}
              AND started.operation_type = 'CASE_SUMMARY_STARTED'
            ORDER BY started.log_id DESC
            LIMIT 1
            """)
    CaseSummaryJobRecord selectJob(@Param("caseId") long caseId, @Param("requestId") String requestId);
}
