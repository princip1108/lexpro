package com.lexpro.lexprobackend.workspace.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.common.web.dto.PageRequest;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import com.lexpro.lexprobackend.workspace.domain.KnowledgeContent;
import com.lexpro.lexprobackend.workspace.domain.WorkTask;
import com.lexpro.lexprobackend.workspace.mapper.WorkspaceMapper;
import com.lexpro.lexprobackend.workspace.web.dto.WorkspaceDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class WorkspaceService {

    private final WorkspaceMapper workspaceMapper;
    private final CaseAccessService caseAccessService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public WorkspaceService(WorkspaceMapper workspaceMapper, CaseAccessService caseAccessService,
                            AuditService auditService, ObjectMapper objectMapper) {
        this.workspaceMapper = workspaceMapper;
        this.caseAccessService = caseAccessService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkspaceDtos.KnowledgeSummaryResponse> listKnowledge(
            boolean contentManager, WorkspaceDtos.KnowledgeQuery query) {
        PageRequest request = query.pageRequest();
        Page<WorkspaceMapper.KnowledgeRow> page = workspaceMapper.selectKnowledgePage(
                new Page<>(request.page(), request.size()), !contentManager, trimToNull(query.keyword()),
                query.contentType(), query.status());
        return PageResponse.from(page, this::knowledgeSummary);
    }

    @Transactional(readOnly = true)
    public WorkspaceDtos.KnowledgeResponse getKnowledge(boolean contentManager, long contentId) {
        WorkspaceMapper.KnowledgeDetailRow row = requireKnowledge(contentId);
        if (!contentManager && !"PUBLISHED".equals(row.status())) {
            throw notFound("Knowledge content", "KNOWLEDGE_NOT_FOUND");
        }
        return knowledgeResponse(row);
    }

    @Transactional(readOnly = true)
    public WorkspaceDtos.KnowledgeStatistics getKnowledgeStatistics(boolean contentManager) {
        WorkspaceMapper.KnowledgeStatsRow stats = workspaceMapper.selectKnowledgeStatistics(!contentManager);
        return new WorkspaceDtos.KnowledgeStatistics(stats.total(), stats.published(), stats.reviewing(), stats.draft(),
                workspaceMapper.selectKnowledgeTypeCounts(!contentManager).stream()
                        .map(row -> new WorkspaceDtos.TypeCount(row.type(), row.count())).toList());
    }

    @Transactional
    public WorkspaceDtos.KnowledgeResponse createKnowledge(long userId, WorkspaceDtos.KnowledgeRequest request) {
        validateKnowledge(request);
        requireOrganization(request.ownerOrganizationId());
        KnowledgeContent content = new KnowledgeContent();
        applyKnowledge(content, request);
        content.setStatus("DRAFT");
        content.setCreatedBy(userId);
        workspaceMapper.insertKnowledge(content);
        auditService.record(new AuditEvent(userId, null, "KNOWLEDGE_CREATED", "KNOWLEDGE_CONTENT",
                String.valueOf(content.getContentId()), AuditResult.SUCCESS,
                Map.of("title", content.getTitle(), "contentType", content.getContentType())));
        return knowledgeResponse(requireKnowledge(content.getContentId()));
    }

    @Transactional
    public WorkspaceDtos.KnowledgeResponse updateKnowledge(
            long userId, long contentId, WorkspaceDtos.KnowledgeRequest request) {
        requireKnowledge(contentId);
        validateKnowledge(request);
        requireOrganization(request.ownerOrganizationId());
        KnowledgeContent content = new KnowledgeContent();
        content.setContentId(contentId);
        applyKnowledge(content, request);
        if (workspaceMapper.updateKnowledge(content) != 1) {
            throw notFound("Knowledge content", "KNOWLEDGE_NOT_FOUND");
        }
        auditService.record(new AuditEvent(userId, null, "KNOWLEDGE_UPDATED", "KNOWLEDGE_CONTENT",
                String.valueOf(contentId), AuditResult.SUCCESS, Map.of("title", content.getTitle())));
        return knowledgeResponse(requireKnowledge(contentId));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkspaceDtos.TaskSummaryResponse> listTasks(long userId, WorkspaceDtos.TaskQuery query) {
        PageRequest request = query.pageRequest();
        Page<WorkspaceMapper.TaskRow> page = workspaceMapper.selectTaskPage(
                new Page<>(request.page(), request.size()), userId, query.mine(), trimToNull(query.keyword()),
                query.status(), query.priority(), query.overdue());
        return PageResponse.from(page, this::taskSummary);
    }

    @Transactional(readOnly = true)
    public WorkspaceDtos.TaskResponse getTask(long userId, long taskId) {
        return taskResponse(requireTask(userId, taskId));
    }

    @Transactional
    public WorkspaceDtos.TaskResponse createTask(long userId, WorkspaceDtos.TaskRequest request) {
        validateTaskSubject(request.caseId(), request.contentId());
        if (request.caseId() != null) {
            caseAccessService.requireEdit(request.caseId(), userId);
        } else {
            requireKnowledge(request.contentId());
        }
        if (request.dueAt() != null && request.dueAt().isBefore(OffsetDateTime.now())) {
            throw badRequest("Task due time must be in the future", "TASK_DUE_AT_INVALID");
        }
        WorkTask task = new WorkTask();
        applyTask(task, request);
        task.setTaskStatus("PENDING");
        task.setAssigneeId(userId);
        task.setCreatedBy(userId);
        workspaceMapper.insertTask(task);
        auditService.record(new AuditEvent(userId, task.getCaseId(), "WORK_TASK_CREATED", "WORK_TASK",
                String.valueOf(task.getTaskId()), AuditResult.SUCCESS,
                Map.of("title", task.getTitle(), "subjectType", task.getCaseId() == null ? "KNOWLEDGE" : "CASE")));
        return taskResponse(requireTask(userId, task.getTaskId()));
    }

    @Transactional
    public WorkspaceDtos.TaskResponse updateTask(
            long userId, long taskId, WorkspaceDtos.TaskRequest request) {
        WorkspaceMapper.TaskDetailRow existing = requireTask(userId, taskId);
        if (existing.caseId() != null) {
            caseAccessService.requireEdit(existing.caseId(), userId);
        }
        validateTaskSubject(request.caseId(), request.contentId());
        if (request.caseId() != null) {
            caseAccessService.requireEdit(request.caseId(), userId);
        } else {
            requireKnowledge(request.contentId());
        }
        if (request.dueAt() != null && request.dueAt().isBefore(existing.createdAt())) {
            throw badRequest("Task due time must not be earlier than its creation time", "TASK_DUE_AT_INVALID");
        }
        WorkTask task = new WorkTask();
        task.setTaskId(taskId);
        applyTask(task, request);
        if (workspaceMapper.updateTask(task) != 1) {
            throw notFound("Work task", "WORK_TASK_NOT_FOUND");
        }
        auditService.record(new AuditEvent(userId, task.getCaseId(), "WORK_TASK_UPDATED", "WORK_TASK",
                String.valueOf(taskId), AuditResult.SUCCESS, Map.of("title", task.getTitle())));
        return taskResponse(requireTask(userId, taskId));
    }

    @Transactional(readOnly = true)
    public WorkspaceDtos.DashboardResponse getDashboard(long userId) {
        WorkspaceMapper.CaseMetricsRow cases = workspaceMapper.selectCaseMetrics(userId);
        WorkspaceMapper.TaskMetricsRow tasks = workspaceMapper.selectTaskMetrics(userId);
        Page<WorkspaceMapper.TaskRow> urgentPage = workspaceMapper.selectTaskPage(
                new Page<>(1, 5, false), userId, true, null, null, "URGENT", null);
        return new WorkspaceDtos.DashboardResponse(
                new WorkspaceDtos.CaseMetrics(cases.total(), cases.active(), cases.pending(), cases.overdue()),
                new WorkspaceDtos.TaskMetrics(tasks.active(), tasks.dueToday(), tasks.overdue(), tasks.waitingConfirmation()),
                workspaceMapper.selectCaseCategories(userId).stream()
                        .map(row -> new WorkspaceDtos.CategoryCount(row.name(), row.count())).toList(),
                workspaceMapper.selectRecentCases(userId, 5).stream().map(row -> row.toResponse()).toList(),
                urgentPage.getRecords().stream().map(this::taskSummary).toList());
    }

    private void validateKnowledge(WorkspaceDtos.KnowledgeRequest request) {
        boolean hasText = trimToNull(request.contentText()) != null;
        JsonNode json = request.contentJson();
        boolean hasJson = json != null && !json.isNull();
        if (!hasText && !hasJson) {
            throw badRequest("contentText or contentJson is required", "KNOWLEDGE_BODY_REQUIRED");
        }
        if (hasJson && !json.isContainerNode()) {
            throw badRequest("contentJson must be an object or array", "KNOWLEDGE_JSON_INVALID");
        }
    }

    private void validateTaskSubject(Long caseId, Long contentId) {
        if ((caseId == null) == (contentId == null)) {
            throw badRequest("Exactly one of caseId or contentId is required", "WORK_TASK_SUBJECT_INVALID");
        }
    }

    private void requireOrganization(Long organizationId) {
        if (organizationId != null && !workspaceMapper.organizationExists(organizationId)) {
            throw notFound("Organization", "ORGANIZATION_NOT_FOUND");
        }
    }

    private WorkspaceMapper.KnowledgeDetailRow requireKnowledge(long contentId) {
        WorkspaceMapper.KnowledgeDetailRow row = workspaceMapper.selectKnowledge(contentId);
        if (row == null) throw notFound("Knowledge content", "KNOWLEDGE_NOT_FOUND");
        return row;
    }

    private WorkspaceMapper.TaskDetailRow requireTask(long userId, long taskId) {
        WorkspaceMapper.TaskDetailRow row = workspaceMapper.selectTask(taskId, userId);
        if (row == null) throw notFound("Work task", "WORK_TASK_NOT_FOUND");
        return row;
    }

    private void applyKnowledge(KnowledgeContent content, WorkspaceDtos.KnowledgeRequest request) {
        content.setContentType(request.contentType());
        content.setTitle(request.title().trim());
        content.setContentText(trimToNull(request.contentText()));
        content.setContentJson(serialize(request.contentJson()));
        content.setOwnerOrganizationId(request.ownerOrganizationId());
    }

    private void applyTask(WorkTask task, WorkspaceDtos.TaskRequest request) {
        task.setCaseId(request.caseId());
        task.setContentId(request.contentId());
        task.setTaskType(request.taskType().trim());
        task.setTitle(request.title().trim());
        task.setDescription(trimToNull(request.description()));
        task.setPriority(request.priority());
        task.setDueAt(request.dueAt());
    }

    private WorkspaceDtos.KnowledgeSummaryResponse knowledgeSummary(WorkspaceMapper.KnowledgeRow row) {
        return new WorkspaceDtos.KnowledgeSummaryResponse(row.contentId(), row.contentType(), row.title(),
                row.ownerOrganizationId(), row.ownerOrganizationName(), row.status(), row.creatorName(), row.updatedAt());
    }

    private WorkspaceDtos.KnowledgeResponse knowledgeResponse(WorkspaceMapper.KnowledgeDetailRow row) {
        return new WorkspaceDtos.KnowledgeResponse(row.contentId(), row.contentType(), row.title(), row.contentText(),
                parse(row.contentJson()), row.ownerOrganizationId(), row.ownerOrganizationName(), row.status(),
                row.createdBy(), row.creatorName(), row.reviewedAt(), row.publishedAt(), row.createdAt(), row.updatedAt());
    }

    private WorkspaceDtos.TaskSummaryResponse taskSummary(WorkspaceMapper.TaskRow row) {
        return new WorkspaceDtos.TaskSummaryResponse(row.taskId(), row.caseId(), row.contentId(), row.subjectTitle(),
                row.taskType(), row.title(), row.taskStatus(), row.priority(), row.assigneeId(), row.assigneeName(),
                row.dueAt(), row.overdue(), row.updatedAt());
    }

    private WorkspaceDtos.TaskResponse taskResponse(WorkspaceMapper.TaskDetailRow row) {
        return new WorkspaceDtos.TaskResponse(row.taskId(), row.caseId(), row.contentId(), row.subjectTitle(),
                row.taskType(), row.title(), row.description(), row.taskStatus(), row.priority(), row.assigneeId(),
                row.assigneeName(), row.createdBy(), row.creatorName(), row.dueAt(), row.overdue(), row.closedAt(),
                row.createdAt(), row.updatedAt());
    }

    private String serialize(JsonNode value) {
        if (value == null || value.isNull()) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw badRequest("contentJson is invalid", "KNOWLEDGE_JSON_INVALID");
        }
    }

    private JsonNode parse(String value) {
        if (value == null) return null;
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored knowledge JSON is invalid", exception);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ApiException badRequest(String detail, String code) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid request", code, detail);
    }

    private ApiException notFound(String title, String code) {
        return new ApiException(HttpStatus.NOT_FOUND, title + " not found", code,
                "The requested " + title.toLowerCase() + " does not exist or is not accessible");
    }
}
