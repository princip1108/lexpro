package com.lexpro.lexprobackend.dossier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexpro.lexprobackend.dossier.domain.EvidenceFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EvidenceFileMapper extends BaseMapper<EvidenceFile> {

    @Select("""
            <script>
            SELECT f.dossier_id, f.case_id, f.folder_id, f.file_name, f.file_type, f.file_url,
                   f.upload_user_id, u.real_name AS upload_user_name, f.uploaded_at,
                   f.file_size, f.file_hash, f.file_status, f.updated_at, f.deleted_at, f.deleted_by
            FROM lexpro.evidence_file f
            JOIN lexpro.app_user u ON u.user_id = f.upload_user_id
            WHERE f.case_id = #{caseId}
            <if test="folderId != null">AND f.folder_id = #{folderId}</if>
            <if test="folderId == null and rootOnly">AND f.folder_id IS NULL</if>
            <if test="!includeDeleted">AND f.file_status = 'ACTIVE'</if>
            ORDER BY f.uploaded_at DESC, f.dossier_id DESC
            </script>
            """)
    List<EvidenceFile> selectByCase(@Param("caseId") long caseId,
                                    @Param("folderId") Long folderId,
                                    @Param("rootOnly") boolean rootOnly,
                                    @Param("includeDeleted") boolean includeDeleted);

    @Select("""
            SELECT f.dossier_id, f.case_id, f.folder_id, f.file_name, f.file_type, f.file_url,
                   f.upload_user_id, u.real_name AS upload_user_name, f.uploaded_at,
                   f.file_size, f.file_hash, f.file_status, f.updated_at, f.deleted_at, f.deleted_by
            FROM lexpro.evidence_file f
            JOIN lexpro.app_user u ON u.user_id = f.upload_user_id
            WHERE f.case_id = #{caseId} AND f.dossier_id = #{dossierId}
            """)
    EvidenceFile selectByCaseAndId(@Param("caseId") long caseId, @Param("dossierId") long dossierId);

    @Update("""
            UPDATE lexpro.evidence_file
            SET file_name = #{fileName}, folder_id = #{folderId}
            WHERE case_id = #{caseId} AND dossier_id = #{dossierId} AND file_status = 'ACTIVE'
            """)
    int updateMetadata(@Param("caseId") long caseId, @Param("dossierId") long dossierId,
                       @Param("fileName") String fileName, @Param("folderId") Long folderId);

    @Update("""
            UPDATE lexpro.evidence_file
            SET file_status = 'DELETED', deleted_at = CURRENT_TIMESTAMP, deleted_by = #{userId}
            WHERE case_id = #{caseId} AND dossier_id = #{dossierId} AND file_status = 'ACTIVE'
            """)
    int softDelete(@Param("caseId") long caseId, @Param("dossierId") long dossierId,
                   @Param("userId") long userId);

    @Update("""
            UPDATE lexpro.evidence_file
            SET file_status = 'ACTIVE', deleted_at = NULL, deleted_by = NULL
            WHERE case_id = #{caseId} AND dossier_id = #{dossierId} AND file_status = 'DELETED'
            """)
    int restore(@Param("caseId") long caseId, @Param("dossierId") long dossierId);
}
