package com.lexpro.lexprobackend.workspace.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexpro.lexprobackend.casework.mapper.CaseSummaryRow;
import com.lexpro.lexprobackend.workspace.domain.KnowledgeContent;
import com.lexpro.lexprobackend.workspace.domain.WorkTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface WorkspaceMapper {

    @Select("""
            <script>
            SELECT k.content_id, k.content_type, k.title, k.owner_organization_id,
                   o.organization_name AS owner_organization_name, k.status,
                   coalesce(u.real_name, u.username) AS creator_name, k.updated_at
            FROM lexpro.knowledge_content k
            LEFT JOIN lexpro.organization_unit o ON o.organization_id = k.owner_organization_id
            LEFT JOIN lexpro.app_user u ON u.user_id = k.created_by
            WHERE (NOT #{publishedOnly} OR k.status = 'PUBLISHED')
            <if test="keyword != null and keyword != ''">
                AND position(lower(#{keyword}) IN lower(k.title || ' ' || coalesce(k.content_text, ''))) &gt; 0
            </if>
            <if test="contentType != null and contentType != ''">AND k.content_type = #{contentType}</if>
            <if test="status != null and status != ''">AND k.status = #{status}</if>
            ORDER BY k.updated_at DESC, k.content_id DESC
            </script>
            """)
    Page<KnowledgeRow> selectKnowledgePage(
            Page<KnowledgeRow> page,
            @Param("publishedOnly") boolean publishedOnly,
            @Param("keyword") String keyword,
            @Param("contentType") String contentType,
            @Param("status") String status
    );

    @Select("""
            SELECT k.content_id, k.content_type, k.title, k.content_text,
                   k.content_json::text AS content_json, k.owner_organization_id,
                   o.organization_name AS owner_organization_name, k.status, k.created_by,
                   coalesce(u.real_name, u.username) AS creator_name,
                   k.reviewed_at, k.published_at, k.created_at, k.updated_at
            FROM lexpro.knowledge_content k
            LEFT JOIN lexpro.organization_unit o ON o.organization_id = k.owner_organization_id
            LEFT JOIN lexpro.app_user u ON u.user_id = k.created_by
            WHERE k.content_id = #{contentId}
            """)
    KnowledgeDetailRow selectKnowledge(@Param("contentId") long contentId);

    @Insert("""
            INSERT INTO lexpro.knowledge_content (
                content_type, title, content_text, content_json,
                owner_organization_id, status, created_by
            ) VALUES (
                #{contentType}, #{title}, #{contentText}, CAST(#{contentJson} AS jsonb),
                #{ownerOrganizationId}, #{status}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "contentId", keyColumn = "content_id")
    int insertKnowledge(KnowledgeContent content);

    @Update("""
            UPDATE lexpro.knowledge_content
            SET content_type = #{contentType}, title = #{title}, content_text = #{contentText},
                content_json = CAST(#{contentJson} AS jsonb),
                owner_organization_id = #{ownerOrganizationId}
            WHERE content_id = #{contentId}
            """)
    int updateKnowledge(KnowledgeContent content);

    @Select("""
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE status = 'PUBLISHED') AS published,
                   count(*) FILTER (WHERE status = 'REVIEWING') AS reviewing,
                   count(*) FILTER (WHERE status = 'DRAFT') AS draft
            FROM lexpro.knowledge_content
            WHERE (NOT #{publishedOnly} OR status = 'PUBLISHED')
            """)
    KnowledgeStatsRow selectKnowledgeStatistics(@Param("publishedOnly") boolean publishedOnly);

    @Select("""
            SELECT content_type AS type, count(*) AS count
            FROM lexpro.knowledge_content
            WHERE (NOT #{publishedOnly} OR status = 'PUBLISHED')
            GROUP BY content_type
            ORDER BY content_type
            """)
    List<TypeCountRow> selectKnowledgeTypeCounts(@Param("publishedOnly") boolean publishedOnly);

    @Select("SELECT EXISTS(SELECT 1 FROM lexpro.organization_unit WHERE organization_id = #{organizationId})")
    boolean organizationExists(@Param("organizationId") long organizationId);

    @Select("""
            <script>
            SELECT t.task_id, t.case_id, t.content_id,
                   coalesce(c.case_name, k.title) AS subject_title,
                   t.task_type, t.title, t.task_status, t.priority,
                   t.assignee_id, coalesce(a.real_name, a.username) AS assignee_name,
                   t.due_at,
                   (t.due_at IS NOT NULL AND t.due_at &lt; CURRENT_TIMESTAMP
                       AND t.task_status NOT IN ('COMPLETED', 'CANCELLED')) AS overdue,
                   t.updated_at
            FROM lexpro.work_task t
            LEFT JOIN lexpro.case_record c ON c.case_id = t.case_id
            LEFT JOIN lexpro.knowledge_content k ON k.content_id = t.content_id
            LEFT JOIN lexpro.app_user a ON a.user_id = t.assignee_id
            WHERE (t.case_id IS NULL OR c.creator_id = #{userId} OR EXISTS (
                SELECT 1 FROM lexpro.case_assignment ca
                WHERE ca.case_id = t.case_id AND ca.user_id = #{userId} AND ca.ended_at IS NULL
            ))
            <if test="mine">AND t.assignee_id = #{userId}</if>
            <if test="keyword != null and keyword != ''">
                AND position(lower(#{keyword}) IN lower(t.title || ' ' || coalesce(t.description, ''))) &gt; 0
            </if>
            <if test="status != null and status != ''">AND t.task_status = #{status}</if>
            <if test="priority != null and priority != ''">AND t.priority = #{priority}</if>
            <if test="overdue != null and overdue">
                AND t.due_at &lt; CURRENT_TIMESTAMP AND t.task_status NOT IN ('COMPLETED', 'CANCELLED')
            </if>
            <if test="overdue != null and !overdue">
                AND (t.due_at IS NULL OR t.due_at &gt;= CURRENT_TIMESTAMP
                    OR t.task_status IN ('COMPLETED', 'CANCELLED'))
            </if>
            ORDER BY
                CASE t.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
                t.due_at ASC NULLS LAST, t.updated_at DESC, t.task_id DESC
            </script>
            """)
    Page<TaskRow> selectTaskPage(
            Page<TaskRow> page,
            @Param("userId") long userId,
            @Param("mine") boolean mine,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("overdue") Boolean overdue
    );

    @Select("""
            SELECT t.task_id, t.case_id, t.content_id,
                   coalesce(c.case_name, k.title) AS subject_title,
                   t.task_type, t.title, t.description, t.task_status, t.priority,
                   t.assignee_id, coalesce(a.real_name, a.username) AS assignee_name,
                   t.created_by, coalesce(creator.real_name, creator.username) AS creator_name,
                   t.due_at,
                   (t.due_at IS NOT NULL AND t.due_at < CURRENT_TIMESTAMP
                       AND t.task_status NOT IN ('COMPLETED', 'CANCELLED')) AS overdue,
                   t.closed_at, t.created_at, t.updated_at
            FROM lexpro.work_task t
            LEFT JOIN lexpro.case_record c ON c.case_id = t.case_id
            LEFT JOIN lexpro.knowledge_content k ON k.content_id = t.content_id
            LEFT JOIN lexpro.app_user a ON a.user_id = t.assignee_id
            LEFT JOIN lexpro.app_user creator ON creator.user_id = t.created_by
            WHERE t.task_id = #{taskId}
              AND (t.case_id IS NULL OR c.creator_id = #{userId} OR EXISTS (
                  SELECT 1 FROM lexpro.case_assignment ca
                  WHERE ca.case_id = t.case_id AND ca.user_id = #{userId} AND ca.ended_at IS NULL
              ))
            """)
    TaskDetailRow selectTask(@Param("taskId") long taskId, @Param("userId") long userId);

    @Insert("""
            INSERT INTO lexpro.work_task (
                case_id, content_id, task_type, title, description, task_status,
                priority, assignee_id, created_by, due_at
            ) VALUES (
                #{caseId}, #{contentId}, #{taskType}, #{title}, #{description}, #{taskStatus},
                #{priority}, #{assigneeId}, #{createdBy}, #{dueAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "taskId", keyColumn = "task_id")
    int insertTask(WorkTask task);

    @Update("""
            UPDATE lexpro.work_task
            SET case_id = #{caseId}, content_id = #{contentId}, task_type = #{taskType},
                title = #{title}, description = #{description}, priority = #{priority}, due_at = #{dueAt}
            WHERE task_id = #{taskId}
            """)
    int updateTask(WorkTask task);

    @Select("""
            SELECT count(*) FILTER (WHERE task_status NOT IN ('COMPLETED', 'CANCELLED')) AS active,
                   count(*) FILTER (WHERE due_at >= date_trunc('day', CURRENT_TIMESTAMP)
                       AND due_at < date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '1 day'
                       AND task_status NOT IN ('COMPLETED', 'CANCELLED')) AS due_today,
                   count(*) FILTER (WHERE due_at < CURRENT_TIMESTAMP
                       AND task_status NOT IN ('COMPLETED', 'CANCELLED')) AS overdue,
                   count(*) FILTER (WHERE task_status = 'WAITING_CONFIRMATION') AS waiting_confirmation
            FROM lexpro.work_task t
            LEFT JOIN lexpro.case_record c ON c.case_id = t.case_id
            WHERE t.assignee_id = #{userId}
              AND (t.case_id IS NULL OR c.creator_id = #{userId} OR EXISTS (
                  SELECT 1 FROM lexpro.case_assignment ca
                  WHERE ca.case_id = t.case_id AND ca.user_id = #{userId} AND ca.ended_at IS NULL
              ))
            """)
    TaskMetricsRow selectTaskMetrics(@Param("userId") long userId);

    @Select("""
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE c.case_status NOT IN ('CLOSED', 'ARCHIVED')) AS active,
                   count(*) FILTER (WHERE c.case_status = 'PENDING') AS pending,
                   count(*) FILTER (WHERE c.deadline_at < CURRENT_TIMESTAMP
                       AND c.case_status NOT IN ('CLOSED', 'ARCHIVED')) AS overdue,
                   count(*) FILTER (WHERE c.case_status = 'PROCESSING') AS reviewing,
                   count(*) FILTER (WHERE c.case_status IN ('CLOSED', 'ARCHIVED')) AS closed
            FROM lexpro.case_record c
            WHERE c.creator_id = #{userId} OR EXISTS (
                SELECT 1 FROM lexpro.case_assignment ca
                WHERE ca.case_id = c.case_id AND ca.user_id = #{userId} AND ca.ended_at IS NULL
            )
            """)
    CaseMetricsRow selectCaseMetrics(@Param("userId") long userId);

    @Select("""
            WITH visible_cases AS (
                SELECT c.case_id
                FROM lexpro.case_record c
                WHERE c.creator_id = #{userId} OR EXISTS (
                    SELECT 1 FROM lexpro.case_assignment ca
                    WHERE ca.case_id = c.case_id AND ca.user_id = #{userId} AND ca.ended_at IS NULL
                )
            )
            SELECT
                (SELECT count(*) FROM lexpro.evidence_file f JOIN visible_cases v ON v.case_id = f.case_id
                    WHERE f.file_status != 'DELETED') AS dossier_total,
                (SELECT count(*) FROM lexpro.entity_result r JOIN visible_cases v ON v.case_id = r.case_id) AS entity_results,
                (SELECT count(*) FROM lexpro.legal_element_result r JOIN visible_cases v ON v.case_id = r.case_id) AS element_results,
                (SELECT count(*) FROM lexpro.case_summary r JOIN visible_cases v ON v.case_id = r.case_id) AS summary_results,
                (SELECT count(*) FROM lexpro.case_report r JOIN visible_cases v ON v.case_id = r.case_id) AS report_total
            """)
    ResultMetricsRow selectResultMetrics(@Param("userId") long userId);

    @Select("""
            SELECT c.case_type AS name, count(*) AS count
            FROM lexpro.case_record c
            WHERE c.creator_id = #{userId} OR EXISTS (
                SELECT 1 FROM lexpro.case_assignment ca
                WHERE ca.case_id = c.case_id AND ca.user_id = #{userId} AND ca.ended_at IS NULL
            )
            GROUP BY c.case_type
            ORDER BY count(*) DESC, c.case_type
            """)
    List<CategoryCountRow> selectCaseCategories(@Param("userId") long userId);

    @Select("""
            SELECT
                c.case_id, c.case_name, c.case_no, c.case_type, c.case_cause,
                c.case_source, c.current_stage, c.case_status, c.accept_date, c.deadline_at,
                (c.deadline_at IS NOT NULL AND c.deadline_at < CURRENT_TIMESTAMP
                    AND c.case_status NOT IN ('CLOSED', 'ARCHIVED')) AS overdue,
                (SELECT p.party_name FROM lexpro.case_party p
                    WHERE p.case_id = c.case_id AND p.party_role = 'SUSPECT'
                    ORDER BY p.party_id LIMIT 1) AS suspect_name,
                (SELECT count(*) FROM lexpro.evidence_file f
                    WHERE f.case_id = c.case_id AND f.file_status != 'DELETED') AS dossier_count,
                (
                    SELECT u.real_name
                    FROM lexpro.case_assignment a
                    JOIN lexpro.app_user u ON u.user_id = a.user_id
                    WHERE a.case_id = c.case_id AND a.ended_at IS NULL
                      AND a.assignment_role IN ('PROSECUTOR', 'ASSIGNEE')
                    ORDER BY CASE a.assignment_role WHEN 'PROSECUTOR' THEN 0 ELSE 1 END,
                             a.assigned_at DESC, a.assignment_id DESC
                    LIMIT 1
                ) AS handler_name,
                c.updated_at
            FROM lexpro.case_record c
            WHERE c.creator_id = #{userId} OR EXISTS (
                SELECT 1 FROM lexpro.case_assignment ca
                WHERE ca.case_id = c.case_id AND ca.user_id = #{userId} AND ca.ended_at IS NULL
            )
            ORDER BY c.updated_at DESC, c.case_id DESC
            LIMIT #{limit}
            """)
    List<CaseSummaryRow> selectRecentCases(@Param("userId") long userId, @Param("limit") int limit);

    record KnowledgeRow(long contentId, String contentType, String title, Long ownerOrganizationId,
                        String ownerOrganizationName, String status, String creatorName,
                        OffsetDateTime updatedAt) {}

    record KnowledgeDetailRow(long contentId, String contentType, String title, String contentText,
                              String contentJson, Long ownerOrganizationId, String ownerOrganizationName,
                              String status, Long createdBy, String creatorName, OffsetDateTime reviewedAt,
                              OffsetDateTime publishedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    record KnowledgeStatsRow(long total, long published, long reviewing, long draft) {}
    record TypeCountRow(String type, long count) {}

    record TaskRow(long taskId, Long caseId, Long contentId, String subjectTitle, String taskType,
                   String title, String taskStatus, String priority, Long assigneeId, String assigneeName,
                   OffsetDateTime dueAt, boolean overdue, OffsetDateTime updatedAt) {}

    record TaskDetailRow(long taskId, Long caseId, Long contentId, String subjectTitle, String taskType,
                         String title, String description, String taskStatus, String priority,
                         Long assigneeId, String assigneeName, Long createdBy, String creatorName,
                         OffsetDateTime dueAt, boolean overdue, OffsetDateTime closedAt,
                         OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    record TaskMetricsRow(long active, long dueToday, long overdue, long waitingConfirmation) {}
    record CaseMetricsRow(long total, long active, long pending, long overdue, long reviewing, long closed) {}
    record ResultMetricsRow(long dossierTotal, long entityResults, long elementResults,
                            long summaryResults, long reportTotal) {}
    record CategoryCountRow(String name, long count) {}
}
