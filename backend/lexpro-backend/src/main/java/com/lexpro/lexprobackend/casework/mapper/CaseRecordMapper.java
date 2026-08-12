package com.lexpro.lexprobackend.casework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexpro.lexprobackend.casework.domain.CaseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CaseRecordMapper extends BaseMapper<CaseRecord> {

    @Select("""
            <script>
            SELECT
                c.case_id, c.case_name, c.case_no, c.case_type, c.case_cause,
                c.case_source, c.current_stage, c.case_status, c.accept_date, c.deadline_at,
                (c.deadline_at IS NOT NULL
                    AND c.deadline_at &lt; CURRENT_TIMESTAMP
                    AND c.case_status NOT IN ('CLOSED', 'ARCHIVED')) AS overdue,
                (
                    SELECT u.real_name
                    FROM lexpro.case_assignment a
                    JOIN lexpro.app_user u ON u.user_id = a.user_id
                    WHERE a.case_id = c.case_id
                      AND a.ended_at IS NULL
                      AND a.assignment_role IN ('PROSECUTOR', 'ASSIGNEE')
                    ORDER BY CASE a.assignment_role WHEN 'PROSECUTOR' THEN 0 ELSE 1 END,
                             a.assigned_at DESC, a.assignment_id DESC
                    LIMIT 1
                ) AS handler_name,
                c.updated_at
            FROM lexpro.case_record c
            WHERE (
                c.creator_id = #{userId}
                OR EXISTS (
                    SELECT 1 FROM lexpro.case_assignment access_assignment
                    WHERE access_assignment.case_id = c.case_id
                      AND access_assignment.user_id = #{userId}
                      AND access_assignment.ended_at IS NULL
                )
            )
            <if test="keyword != null and keyword != ''">
                AND position(lower(#{keyword}) IN lower(c.case_name || ' ' || coalesce(c.case_no, ''))) &gt; 0
            </if>
            <if test="status != null and status != ''">AND c.case_status = #{status}</if>
            <if test="caseType != null and caseType != ''">AND c.case_type = #{caseType}</if>
            <if test="overdue != null and overdue">
                AND c.deadline_at IS NOT NULL AND c.deadline_at &lt; CURRENT_TIMESTAMP
                AND c.case_status NOT IN ('CLOSED', 'ARCHIVED')
            </if>
            <if test="overdue != null and !overdue">
                AND (c.deadline_at IS NULL OR c.deadline_at &gt;= CURRENT_TIMESTAMP
                    OR c.case_status IN ('CLOSED', 'ARCHIVED'))
            </if>
            ORDER BY
                CASE WHEN c.deadline_at IS NOT NULL
                          AND c.deadline_at &lt; CURRENT_TIMESTAMP
                          AND c.case_status NOT IN ('CLOSED', 'ARCHIVED') THEN 0 ELSE 1 END,
                c.deadline_at ASC NULLS LAST, c.updated_at DESC, c.case_id DESC
            </script>
            """)
    Page<CaseSummaryRow> selectVisiblePage(
            Page<CaseSummaryRow> page,
            @Param("userId") long userId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("caseType") String caseType,
            @Param("overdue") Boolean overdue
    );

    @Select("""
            SELECT CASE
                WHEN c.creator_id = #{userId} THEN 3
                ELSE coalesce(MAX(CASE a.access_level
                    WHEN 'MANAGE' THEN 3 WHEN 'EDIT' THEN 2 WHEN 'VIEW' THEN 1 ELSE 0
                END), 0)
            END
            FROM lexpro.case_record c
            LEFT JOIN lexpro.case_assignment a
              ON a.case_id = c.case_id AND a.user_id = #{userId} AND a.ended_at IS NULL
            WHERE c.case_id = #{caseId}
            GROUP BY c.case_id, c.creator_id
            """)
    Integer selectAccessRank(@Param("caseId") long caseId, @Param("userId") long userId);

    @Select("SELECT EXISTS(SELECT 1 FROM lexpro.case_record WHERE case_no = #{caseNo})")
    boolean existsByCaseNo(@Param("caseNo") String caseNo);

    @Update("""
            UPDATE lexpro.case_record
            SET case_name = #{record.caseName},
                case_no = #{record.caseNo},
                case_type = #{record.caseType},
                case_cause = #{record.caseCause},
                case_source = #{record.caseSource},
                current_stage = #{record.currentStage},
                accept_date = #{record.acceptDate},
                deadline_at = #{record.deadlineAt}
            WHERE case_id = #{record.caseId}
            """)
    int updateMetadata(@Param("record") CaseRecord record);
}
