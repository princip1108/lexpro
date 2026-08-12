package com.lexpro.lexprobackend.dossier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexpro.lexprobackend.dossier.domain.DossierFolder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DossierFolderMapper extends BaseMapper<DossierFolder> {

    @Select("""
            SELECT folder_id, case_id, parent_folder_id, folder_name, sort_no, created_by, created_at
            FROM lexpro.dossier_folder
            WHERE case_id = #{caseId}
            ORDER BY sort_no NULLS LAST, lower(folder_name), folder_id
            """)
    List<DossierFolder> selectByCase(@Param("caseId") long caseId);

    @Select("""
            SELECT folder_id, case_id, parent_folder_id, folder_name, sort_no, created_by, created_at
            FROM lexpro.dossier_folder
            WHERE case_id = #{caseId} AND folder_id = #{folderId}
            """)
    DossierFolder selectByCaseAndId(@Param("caseId") long caseId, @Param("folderId") long folderId);
}
