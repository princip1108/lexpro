package com.lexpro.lexprobackend.dossier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexpro.lexprobackend.dossier.domain.FileTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FileTagMapper extends BaseMapper<FileTag> {

    @Select("""
            SELECT tag_id, case_id, tag_name, color, created_by, created_at
            FROM lexpro.file_tag
            WHERE case_id = #{caseId}
            ORDER BY lower(tag_name), tag_id
            """)
    List<FileTag> selectByCase(@Param("caseId") long caseId);

    @Select("""
            SELECT tag_id, case_id, tag_name, color, created_by, created_at
            FROM lexpro.file_tag
            WHERE case_id = #{caseId} AND tag_id = #{tagId}
            """)
    FileTag selectByCaseAndId(@Param("caseId") long caseId, @Param("tagId") long tagId);
}
