package com.lexpro.lexprobackend.casework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexpro.lexprobackend.casework.domain.CaseAssignment;
import com.lexpro.lexprobackend.casework.web.dto.CaseAssignmentResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CaseAssignmentMapper extends BaseMapper<CaseAssignment> {

    @Select("""
            SELECT a.assignment_id, a.case_id, a.user_id, u.username, u.real_name,
                   a.assignment_role, a.access_level, a.assigned_at, a.ended_at,
                   a.assigned_by, assigner.real_name AS assigned_by_name
            FROM lexpro.case_assignment a
            JOIN lexpro.app_user u ON u.user_id = a.user_id
            LEFT JOIN lexpro.app_user assigner ON assigner.user_id = a.assigned_by
            WHERE a.case_id = #{caseId}
            ORDER BY CASE WHEN a.ended_at IS NULL THEN 0 ELSE 1 END,
                     a.assigned_at DESC, a.assignment_id DESC
            """)
    List<CaseAssignmentResponse> selectHistory(@Param("caseId") long caseId);
}
