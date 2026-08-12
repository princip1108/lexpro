package com.lexpro.lexprobackend.processing.mapper;

import com.lexpro.lexprobackend.processing.domain.DocumentParseJob;
import com.lexpro.lexprobackend.processing.domain.DocumentParseResult;
import com.lexpro.lexprobackend.processing.domain.ParseSourceFile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface DocumentParseMapper {

    @Select("""
            SELECT dossier_id, case_id, file_name, file_type, file_url, file_status
            FROM lexpro.evidence_file
            WHERE case_id = #{caseId} AND dossier_id = #{dossierId} AND file_status = 'ACTIVE'
            FOR UPDATE
            """)
    ParseSourceFile lockActiveSource(@Param("caseId") long caseId, @Param("dossierId") long dossierId);

    @Select("""
            SELECT dossier_id, case_id, file_name, file_type, file_url, file_status
            FROM lexpro.evidence_file
            WHERE case_id = #{caseId} AND dossier_id = #{dossierId}
            """)
    ParseSourceFile selectSource(@Param("caseId") long caseId, @Param("dossierId") long dossierId);

    @Select("""
            SELECT doc_id, dossier_id, case_id, version_no, parse_status, parser_version,
                   error_message, created_at, completed_at, requested_by, request_id,
                   parser_parameters_json::text AS parser_parameters_json, duration_ms, is_current
            FROM lexpro.document_parse_result
            WHERE case_id = #{caseId} AND dossier_id = #{dossierId} AND is_current
            """)
    DocumentParseResult selectCurrent(@Param("caseId") long caseId, @Param("dossierId") long dossierId);

    @Select("""
            SELECT COALESCE(MAX(version_no), 0) + 1
            FROM lexpro.document_parse_result
            WHERE dossier_id = #{dossierId}
            """)
    int selectNextVersion(@Param("dossierId") long dossierId);

    @Update("""
            UPDATE lexpro.document_parse_result
            SET is_current = false
            WHERE dossier_id = #{dossierId} AND is_current
            """)
    int clearCurrent(@Param("dossierId") long dossierId);

    @Insert("""
            INSERT INTO lexpro.document_parse_result (
                dossier_id, case_id, version_no, parse_status, requested_by, request_id,
                parser_parameters_json, is_current
            ) VALUES (
                #{dossierId}, #{caseId}, #{versionNo}, 'PROCESSING', #{requestedBy}, #{requestId},
                CAST(#{parserParametersJson} AS jsonb), true
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "docId", keyColumn = "doc_id")
    int insert(DocumentParseResult result);

    @Select("""
            SELECT doc_id, dossier_id, case_id, version_no, parse_status, parser_version,
                   error_message, created_at, completed_at, requested_by, request_id,
                   parser_parameters_json::text AS parser_parameters_json, duration_ms, is_current
            FROM lexpro.document_parse_result
            WHERE case_id = #{caseId} AND dossier_id = #{dossierId}
            ORDER BY version_no DESC, doc_id DESC
            """)
    List<DocumentParseResult> selectHistory(@Param("caseId") long caseId,
                                            @Param("dossierId") long dossierId);

    @Select("""
            SELECT doc_id, dossier_id, case_id, version_no, parse_status,
                   parsed_text_json::text AS parsed_text_json, raw_text, parser_version,
                   error_message, created_at, completed_at, requested_by, request_id,
                   parser_parameters_json::text AS parser_parameters_json, duration_ms, is_current
            FROM lexpro.document_parse_result
            WHERE case_id = #{caseId} AND doc_id = #{docId}
            """)
    DocumentParseResult selectDetail(@Param("caseId") long caseId, @Param("docId") long docId);

    @Select("""
            SELECT d.doc_id, d.dossier_id, d.case_id, f.file_name, f.file_type, f.file_url,
                   f.file_status, d.parser_parameters_json::text AS parser_parameters_json,
                   d.requested_by, d.request_id, d.version_no
            FROM lexpro.document_parse_result d
            JOIN lexpro.evidence_file f
              ON f.dossier_id = d.dossier_id AND f.case_id = d.case_id
            WHERE d.doc_id = #{docId} AND d.parse_status = 'PROCESSING'
            """)
    DocumentParseJob selectJob(@Param("docId") long docId);

    @Update("""
            UPDATE lexpro.document_parse_result
            SET parse_status = 'SUCCESS', raw_text = #{rawText},
                parsed_text_json = CAST(#{parsedTextJson} AS jsonb),
                parser_version = #{parserVersion}, error_message = NULL,
                duration_ms = #{durationMs}, completed_at = CURRENT_TIMESTAMP
            WHERE doc_id = #{docId} AND parse_status = 'PROCESSING'
            """)
    int markSuccess(@Param("docId") long docId, @Param("rawText") String rawText,
                    @Param("parsedTextJson") String parsedTextJson,
                    @Param("parserVersion") String parserVersion, @Param("durationMs") int durationMs);

    @Update("""
            UPDATE lexpro.document_parse_result
            SET parse_status = 'FAILED', error_message = #{errorCode},
                duration_ms = #{durationMs}, completed_at = CURRENT_TIMESTAMP
            WHERE doc_id = #{docId} AND parse_status = 'PROCESSING'
            """)
    int markFailed(@Param("docId") long docId, @Param("errorCode") String errorCode,
                   @Param("durationMs") int durationMs);

    @Update("""
            UPDATE lexpro.document_parse_result
            SET parse_status = 'FAILED', error_message = 'PROCESSING_INTERRUPTED',
                duration_ms = 0, completed_at = CURRENT_TIMESTAMP
            WHERE doc_id = #{docId} AND parse_status = 'PROCESSING' AND created_at = #{createdAt}
            """)
    int markStaleFailed(@Param("docId") long docId, @Param("createdAt") OffsetDateTime createdAt);
}
