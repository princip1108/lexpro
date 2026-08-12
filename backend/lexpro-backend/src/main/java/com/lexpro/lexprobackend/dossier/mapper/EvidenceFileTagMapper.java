package com.lexpro.lexprobackend.dossier.mapper;

import com.lexpro.lexprobackend.dossier.domain.FileTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EvidenceFileTagMapper {

    @Select("""
            SELECT eft.dossier_id, t.tag_id, t.case_id, t.tag_name, t.color, t.created_by, t.created_at
            FROM lexpro.evidence_file_tag eft
            JOIN lexpro.evidence_file f ON f.dossier_id = eft.dossier_id
            JOIN lexpro.file_tag t ON t.tag_id = eft.tag_id
            WHERE f.case_id = #{caseId}
            ORDER BY lower(t.tag_name), t.tag_id
            """)
    List<EvidenceFileTagRow> selectByCase(@Param("caseId") long caseId);

    @Delete("DELETE FROM lexpro.evidence_file_tag WHERE dossier_id = #{dossierId}")
    int deleteByDossierId(@Param("dossierId") long dossierId);

    @Insert("""
            INSERT INTO lexpro.evidence_file_tag (dossier_id, tag_id, created_by)
            VALUES (#{dossierId}, #{tagId}, #{createdBy})
            """)
    int insertLink(@Param("dossierId") long dossierId, @Param("tagId") long tagId,
                   @Param("createdBy") long createdBy);

    record EvidenceFileTagRow(Long dossierId, Long tagId, Long caseId, String tagName, String color,
                              Long createdBy, java.time.OffsetDateTime createdAt) {
        public FileTag toTag() {
            FileTag tag = new FileTag();
            tag.setTagId(tagId);
            tag.setCaseId(caseId);
            tag.setTagName(tagName);
            tag.setColor(color);
            tag.setCreatedBy(createdBy);
            tag.setCreatedAt(createdAt);
            return tag;
        }
    }
}
