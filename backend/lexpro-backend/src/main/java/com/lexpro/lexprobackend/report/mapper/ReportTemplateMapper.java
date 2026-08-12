package com.lexpro.lexprobackend.report.mapper;

import com.lexpro.lexprobackend.report.domain.ReportTemplate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReportTemplateMapper {

    @Select("""
            SELECT template_id
            FROM lexpro.report_template
            WHERE template_code = #{templateCode}
            FOR UPDATE
            """)
    List<Long> lockVersions(@Param("templateCode") String templateCode);

    @Select("""
            SELECT COALESCE(max(version_no), 0) + 1
            FROM lexpro.report_template
            WHERE template_code = #{templateCode}
            """)
    int selectNextVersion(@Param("templateCode") String templateCode);

    @Insert("""
            INSERT INTO lexpro.report_template (
                template_code, template_name, template_type, template_content_json,
                version_no, schema_version, status, created_by
            ) VALUES (
                #{templateCode}, #{templateName}, #{templateType}, CAST(#{templateContentJson} AS jsonb),
                #{versionNo}, #{schemaVersion}, 'DRAFT', #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "templateId", keyColumn = "template_id")
    int insert(ReportTemplate template);

    @Select("""
            SELECT template_id, template_code, template_name, template_type,
                   template_content_json::text AS template_content_json, version_no,
                   schema_version, status, created_by, created_at
            FROM lexpro.report_template
            WHERE (CAST(#{templateType} AS varchar) IS NULL OR template_type = CAST(#{templateType} AS varchar))
              AND (CAST(#{status} AS varchar) IS NULL OR status = CAST(#{status} AS varchar))
            ORDER BY template_code, version_no DESC
            """)
    List<ReportTemplate> selectList(@Param("templateType") String templateType,
                                    @Param("status") String status);

    @Select("""
            SELECT template_id, template_code, template_name, template_type,
                   template_content_json::text AS template_content_json, version_no,
                   schema_version, status, created_by, created_at
            FROM lexpro.report_template
            WHERE template_id = #{templateId}
            """)
    ReportTemplate selectById(@Param("templateId") long templateId);

    @Select("""
            SELECT template_id, template_code, template_name, template_type,
                   template_content_json::text AS template_content_json, version_no,
                   schema_version, status, created_by, created_at
            FROM lexpro.report_template
            WHERE template_code = #{templateCode}
            ORDER BY version_no DESC
            LIMIT 1
            """)
    ReportTemplate selectLatestByCode(@Param("templateCode") String templateCode);

    @Update("""
            UPDATE lexpro.report_template
            SET status = 'DISABLED'
            WHERE template_code = #{templateCode} AND status = 'ACTIVE' AND template_id <> #{templateId}
            """)
    int disableOtherActive(@Param("templateCode") String templateCode,
                           @Param("templateId") long templateId);

    @Update("""
            UPDATE lexpro.report_template
            SET status = 'ACTIVE'
            WHERE template_id = #{templateId} AND status = 'DRAFT'
            """)
    int activate(@Param("templateId") long templateId);

    @Update("""
            UPDATE lexpro.report_template
            SET status = 'DISABLED'
            WHERE template_id = #{templateId} AND status = 'ACTIVE'
            """)
    int disable(@Param("templateId") long templateId);
}
