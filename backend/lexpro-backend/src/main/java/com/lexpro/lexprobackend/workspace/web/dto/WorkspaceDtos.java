package com.lexpro.lexprobackend.workspace.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.lexpro.lexprobackend.casework.web.dto.CaseSummaryResponse;
import com.lexpro.lexprobackend.common.web.dto.PageRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    public record KnowledgeQuery(
            @Min(1) Integer page,
            @Min(1) @Max(100) Integer size,
            @Size(max = 255) String keyword,
            @Pattern(regexp = "KNOWLEDGE|RULE|CHECKLIST") String contentType,
            @Pattern(regexp = "DRAFT|REVIEWING|PUBLISHED|REJECTED|ARCHIVED") String status
    ) {
        public PageRequest pageRequest() { return new PageRequest(page, size); }
    }

    public record KnowledgeRequest(
            @NotBlank @Pattern(regexp = "KNOWLEDGE|RULE|CHECKLIST") String contentType,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 200000) String contentText,
            JsonNode contentJson,
            Long ownerOrganizationId
    ) {}

    public record KnowledgeResponse(
            long contentId,
            String contentType,
            String title,
            String contentText,
            JsonNode contentJson,
            Long ownerOrganizationId,
            String ownerOrganizationName,
            String status,
            Long createdBy,
            String creatorName,
            OffsetDateTime reviewedAt,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record KnowledgeSummaryResponse(
            long contentId,
            String contentType,
            String title,
            Long ownerOrganizationId,
            String ownerOrganizationName,
            String status,
            String creatorName,
            OffsetDateTime updatedAt
    ) {}

    public record KnowledgeStatistics(
            long total,
            long published,
            long reviewing,
            long draft,
            List<TypeCount> types
    ) {}

    public record TypeCount(String type, long count) {}

    public record TaskQuery(
            @Min(1) Integer page,
            @Min(1) @Max(100) Integer size,
            @Size(max = 255) String keyword,
            @Pattern(regexp = "PENDING|PROCESSING|WAITING_CONFIRMATION|COMPLETED|CANCELLED") String status,
            @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
            Boolean overdue,
            Boolean mine
    ) {
        public TaskQuery {
            mine = mine == null ? Boolean.TRUE : mine;
        }

        public PageRequest pageRequest() { return new PageRequest(page, size); }
    }

    public record TaskRequest(
            Long caseId,
            Long contentId,
            @NotBlank @Size(max = 50) String taskType,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 10000) String description,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
            OffsetDateTime dueAt
    ) {}

    public record TaskResponse(
            long taskId,
            Long caseId,
            Long contentId,
            String subjectTitle,
            String taskType,
            String title,
            String description,
            String taskStatus,
            String priority,
            Long assigneeId,
            String assigneeName,
            Long createdBy,
            String creatorName,
            OffsetDateTime dueAt,
            boolean overdue,
            OffsetDateTime closedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record TaskSummaryResponse(
            long taskId,
            Long caseId,
            Long contentId,
            String subjectTitle,
            String taskType,
            String title,
            String taskStatus,
            String priority,
            Long assigneeId,
            String assigneeName,
            OffsetDateTime dueAt,
            boolean overdue,
            OffsetDateTime updatedAt
    ) {}

    public record DashboardResponse(
            CaseMetrics cases,
            TaskMetrics tasks,
            ResultMetrics results,
            List<CategoryCount> caseCategories,
            List<CaseSummaryResponse> recentCases,
            List<TaskSummaryResponse> urgentTasks
    ) {}

    public record CaseMetrics(long total, long active, long pending, long overdue, long reviewing, long closed) {}

    public record TaskMetrics(long active, long dueToday, long overdue, long waitingConfirmation) {}

    public record ResultMetrics(long dossierTotal, long entityResults, long elementResults,
                                long summaryResults, long reportTotal) {}

    public record CategoryCount(String name, long count) {}
}
