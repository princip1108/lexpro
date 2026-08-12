package com.lexpro.lexprobackend.report.mapper;

import com.lexpro.lexprobackend.report.domain.CaseReport;
import com.lexpro.lexprobackend.report.domain.ReportEvidenceReference;
import com.lexpro.lexprobackend.report.domain.ReportJobRecord;
import com.lexpro.lexprobackend.report.domain.ReportSourceMaterial;
import com.lexpro.lexprobackend.report.domain.ReportTypicalCaseReference;
import com.lexpro.lexprobackend.report.domain.ReportExportData;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CaseReportMapper {

    @Select("SELECT case_id FROM lexpro.case_record WHERE case_id = #{caseId} FOR UPDATE")
    Long lockCase(@Param("caseId") long caseId);

    @Select("""
            SELECT COALESCE(max(version_no), 0) + 1
            FROM lexpro.case_report
            WHERE case_id = #{caseId} AND report_type = #{reportType}
            """)
    int selectNextVersion(@Param("caseId") long caseId, @Param("reportType") String reportType);

    @Select("""
            SELECT 'CASE_CARD'::text AS source_type, t.fill_task_id AS source_id,
                   'case card'::text AS label,
                   CAST(jsonb_agg(jsonb_build_object(
                       'fieldCode', f.field_code, 'fieldName', f.field_name,
                       'value', COALESCE(f.field_value_json, to_jsonb(f.field_value)),
                       'sourceText', f.source_text, 'confidence', f.confidence
                   ) ORDER BY f.field_id) FILTER (WHERE f.field_id IS NOT NULL) AS text) AS content,
                   (t.fill_status = 'CONFIRMED' AND count(f.field_id) > 0) AS ready
            FROM lexpro.case_card_fill_task t
            LEFT JOIN lexpro.case_card_field f ON f.fill_task_id = t.fill_task_id
                                              AND f.confirm_status = 'CONFIRMED'
            WHERE t.case_id = #{caseId} AND t.fill_task_id = #{fillTaskId}
            GROUP BY t.fill_task_id, t.fill_status
            """)
    ReportSourceMaterial selectCardSource(@Param("caseId") long caseId,
                                          @Param("fillTaskId") long fillTaskId);

    @Select("""
            SELECT 'EVIDENCE'::text AS source_type, f.dossier_id AS source_id,
                   f.file_name AS label, d.raw_text AS content,
                   (f.file_status = 'ACTIVE' AND f.deleted_at IS NULL
                    AND d.parse_status = 'SUCCESS' AND nullif(btrim(d.raw_text), '') IS NOT NULL) AS ready
            FROM lexpro.evidence_file f
            LEFT JOIN lexpro.document_parse_result d
              ON d.dossier_id = f.dossier_id AND d.case_id = f.case_id AND d.is_current
            WHERE f.case_id = #{caseId} AND f.dossier_id = #{dossierId}
            """)
    ReportSourceMaterial selectEvidenceSource(@Param("caseId") long caseId,
                                               @Param("dossierId") long dossierId);

    @Select("""
            SELECT 'LEGAL_ELEMENT'::text AS source_type, l.element_result_id AS source_id,
                   COALESCE(l.case_cause, 'legal elements') AS label,
                   COALESCE(l.final_elements_json, l.raw_elements_json)::text AS content,
                   (COALESCE(l.final_elements_json, l.raw_elements_json) IS NOT NULL) AS ready
            FROM lexpro.legal_element_result l
            WHERE l.case_id = #{caseId} AND l.element_result_id = #{elementResultId}
            """)
    ReportSourceMaterial selectLegalElementSource(@Param("caseId") long caseId,
                                                   @Param("elementResultId") long elementResultId);

    @Select("""
            SELECT 'TYPICAL_CASE'::text AS source_type, t.typical_case_id AS source_id,
                   t.title AS label,
                   concat_ws(E'\n', t.title, c.fact, c.content) AS content,
                   (nullif(btrim(concat_ws(E'\n', t.title, c.fact, c.content)), '') IS NOT NULL) AS ready
            FROM lexpro.typical_case t
            LEFT JOIN lexpro.typical_case_content c ON c.typical_case_id = t.typical_case_id
            WHERE t.typical_case_id = #{typicalCaseId}
            """)
    ReportSourceMaterial selectTypicalCaseSource(@Param("typicalCaseId") long typicalCaseId);

    @Select("""
            SELECT count(*)
            FROM lexpro.case_recommendation_item
            WHERE item_id = #{itemId} AND case_id = #{caseId} AND typical_case_id = #{typicalCaseId}
            """)
    int countRecommendationItem(@Param("caseId") long caseId, @Param("itemId") long itemId,
                                @Param("typicalCaseId") long typicalCaseId);

    @Insert("""
            INSERT INTO lexpro.case_report (
                case_id, template_id, card_fill_task_id, version_no, report_type, generate_mode,
                operator_id, report_status, report_title, is_current, created_at, created_by,
                content_schema_version, model_name, prompt_version, generation_parameters_json,
                request_id
            ) VALUES (
                #{caseId}, #{templateId}, #{cardFillTaskId}, #{versionNo}, #{reportType}, 'AUTO',
                #{operatorId}, 'GENERATING', #{reportTitle}, false, CURRENT_TIMESTAMP, #{createdBy},
                #{contentSchemaVersion}, #{modelName}, #{promptVersion}, '{}'::jsonb, #{requestId}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "reportId", keyColumn = "report_id")
    int insert(CaseReport report);

    @Insert("""
            INSERT INTO lexpro.report_evidence (report_id, dossier_id, sort_no, case_id)
            VALUES (#{reportId}, #{dossierId}, #{sortNo}, #{caseId})
            """)
    int insertEvidenceReference(@Param("reportId") long reportId, @Param("caseId") long caseId,
                                @Param("dossierId") long dossierId, @Param("sortNo") Integer sortNo);

    @Insert("""
            INSERT INTO lexpro.report_legal_element_result (report_id, element_result_id, case_id)
            VALUES (#{reportId}, #{elementResultId}, #{caseId})
            """)
    int insertLegalElementReference(@Param("reportId") long reportId, @Param("caseId") long caseId,
                                    @Param("elementResultId") long elementResultId);

    @Insert("""
            INSERT INTO lexpro.report_typical_case_reference (
                report_id, case_id, typical_case_id, recommendation_item_id,
                section_code, citation_note, sort_no, cited_by
            ) VALUES (
                #{reportId}, #{caseId}, #{typicalCaseId}, #{recommendationItemId},
                #{sectionCode}, #{citationNote}, #{sortNo}, #{citedBy}
            )
            """)
    int insertTypicalCaseReference(@Param("reportId") long reportId, @Param("caseId") long caseId,
                                   @Param("typicalCaseId") long typicalCaseId,
                                   @Param("recommendationItemId") Long recommendationItemId,
                                   @Param("sectionCode") String sectionCode,
                                   @Param("citationNote") String citationNote,
                                   @Param("sortNo") Integer sortNo, @Param("citedBy") long citedBy);

    @Select("""
            SELECT report_id, case_id, template_id, card_fill_task_id, version_no, report_type,
                   generate_mode, operator_id, report_status, report_title,
                   report_content_json::text AS report_content_json, error_message, generated_at,
                   updated_at, finalized_at, finalized_by, is_current AS current, created_at, created_by,
                   content_schema_version, model_name, model_version, prompt_version, prompt_snapshot,
                   generation_parameters_json::text AS generation_parameters_json,
                   token_usage_json::text AS token_usage_json, request_id, duration_ms, lock_version
            FROM lexpro.case_report
            WHERE case_id = #{caseId}
              AND (CAST(#{reportType} AS varchar) IS NULL OR report_type = CAST(#{reportType} AS varchar))
            ORDER BY created_at DESC, report_id DESC
            """)
    List<CaseReport> selectHistory(@Param("caseId") long caseId, @Param("reportType") String reportType);

    @Select("""
            SELECT report_id, case_id, template_id, card_fill_task_id, version_no, report_type,
                   generate_mode, operator_id, report_status, report_title,
                   report_content_json::text AS report_content_json, error_message, generated_at,
                   updated_at, finalized_at, finalized_by, is_current AS current, created_at, created_by,
                   content_schema_version, model_name, model_version, prompt_version, prompt_snapshot,
                   generation_parameters_json::text AS generation_parameters_json,
                   token_usage_json::text AS token_usage_json, request_id, duration_ms, lock_version
            FROM lexpro.case_report
            WHERE case_id = #{caseId} AND report_id = #{reportId}
            """)
    CaseReport selectDetail(@Param("caseId") long caseId, @Param("reportId") long reportId);

    @Select("""
            SELECT dossier_id, sort_no FROM lexpro.report_evidence
            WHERE case_id = #{caseId} AND report_id = #{reportId}
            ORDER BY sort_no NULLS LAST, dossier_id
            """)
    List<ReportEvidenceReference> selectEvidenceReferences(@Param("caseId") long caseId,
                                                           @Param("reportId") long reportId);

    @Select("""
            SELECT element_result_id FROM lexpro.report_legal_element_result
            WHERE case_id = #{caseId} AND report_id = #{reportId}
            ORDER BY element_result_id
            """)
    List<Long> selectLegalElementReferences(@Param("caseId") long caseId,
                                            @Param("reportId") long reportId);

    @Select("""
            SELECT typical_case_id, recommendation_item_id, section_code, citation_note,
                   sort_no, cited_by, created_at
            FROM lexpro.report_typical_case_reference
            WHERE case_id = #{caseId} AND report_id = #{reportId}
            ORDER BY sort_no NULLS LAST, typical_case_id
            """)
    List<ReportTypicalCaseReference> selectTypicalCaseReferences(@Param("caseId") long caseId,
                                                                 @Param("reportId") long reportId);

    @Update("""
            UPDATE lexpro.case_report SET is_current = false
            WHERE case_id = #{caseId} AND report_type = #{reportType} AND is_current
            """)
    int clearCurrent(@Param("caseId") long caseId, @Param("reportType") String reportType);

    @Update("""
            UPDATE lexpro.case_report
            SET report_status = 'DRAFT', report_content_json = CAST(#{reportContentJson} AS jsonb),
                model_version = #{modelVersion}, prompt_snapshot = #{promptSnapshot},
                generation_parameters_json = CAST(#{generationParametersJson} AS jsonb),
                token_usage_json = CAST(#{tokenUsageJson} AS jsonb), duration_ms = #{durationMs},
                generated_at = CURRENT_TIMESTAMP, is_current = true
            WHERE case_id = #{caseId} AND report_id = #{reportId} AND report_status = 'GENERATING'
            """)
    int complete(CaseReport report);

    @Update("""
            UPDATE lexpro.case_report
            SET report_status = 'FAILED', error_message = #{errorCode}, duration_ms = #{durationMs},
                is_current = false
            WHERE case_id = #{caseId} AND report_id = #{reportId} AND report_status = 'GENERATING'
            """)
    int fail(@Param("caseId") long caseId, @Param("reportId") long reportId,
             @Param("errorCode") String errorCode, @Param("durationMs") int durationMs);

    @Update("""
            UPDATE lexpro.case_report
            SET report_title = #{reportTitle}, report_content_json = CAST(#{reportContentJson} AS jsonb),
                lock_version = lock_version + 1
            WHERE case_id = #{caseId} AND report_id = #{reportId}
              AND report_status = 'DRAFT' AND lock_version = #{lockVersion}
            """)
    int updateDraft(CaseReport report);

    @Update("""
            UPDATE lexpro.case_report
            SET report_status = 'REVIEWING', lock_version = lock_version + 1
            WHERE case_id = #{caseId} AND report_id = #{reportId}
              AND report_status = 'DRAFT' AND is_current AND lock_version = #{lockVersion}
            """)
    int submitReview(@Param("caseId") long caseId, @Param("reportId") long reportId,
                     @Param("lockVersion") int lockVersion);

    @Update("""
            UPDATE lexpro.case_report
            SET report_status = 'DRAFT', lock_version = lock_version + 1
            WHERE case_id = #{caseId} AND report_id = #{reportId}
              AND report_status = 'REVIEWING' AND is_current AND lock_version = #{lockVersion}
            """)
    int returnToDraft(@Param("caseId") long caseId, @Param("reportId") long reportId,
                      @Param("lockVersion") int lockVersion);

    @Update("""
            UPDATE lexpro.case_report
            SET report_status = 'FINAL', finalized_by = #{userId}, finalized_at = CURRENT_TIMESTAMP,
                lock_version = lock_version + 1
            WHERE case_id = #{caseId} AND report_id = #{reportId}
              AND report_status = 'REVIEWING' AND is_current AND lock_version = #{lockVersion}
            """)
    int finalizeReport(@Param("caseId") long caseId, @Param("reportId") long reportId,
                       @Param("userId") long userId, @Param("lockVersion") int lockVersion);

    @Select("""
            SELECT request_id, report_id, report_type,
                   CASE WHEN report_status = 'GENERATING' THEN 'PROCESSING'
                        WHEN report_status = 'FAILED' THEN 'FAILED' ELSE 'SUCCESS' END AS job_status,
                   CASE WHEN report_status = 'FAILED' THEN error_message END AS error_code,
                   created_at AS requested_at,
                   CASE WHEN report_status = 'GENERATING' THEN NULL
                        ELSE COALESCE(generated_at, updated_at) END AS completed_at
            FROM lexpro.case_report
            WHERE case_id = #{caseId} AND request_id = #{requestId}
            """)
    ReportJobRecord selectJob(@Param("caseId") long caseId, @Param("requestId") String requestId);

    @Select("""
            SELECT r.report_id, r.case_id, r.template_id, r.report_type, r.report_status,
                   r.report_title, r.report_content_json::text AS report_content_json,
                   r.version_no, c.case_no, c.case_name
            FROM lexpro.case_report r
            JOIN lexpro.case_record c ON c.case_id = r.case_id
            WHERE r.case_id = #{caseId} AND r.report_id = #{reportId}
            """)
    ReportExportData selectExportData(@Param("caseId") long caseId, @Param("reportId") long reportId);
}
