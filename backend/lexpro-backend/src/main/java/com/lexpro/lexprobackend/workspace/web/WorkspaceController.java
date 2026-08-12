package com.lexpro.lexprobackend.workspace.web;

import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import com.lexpro.lexprobackend.workspace.service.WorkspaceService;
import com.lexpro.lexprobackend.workspace.web.dto.WorkspaceDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/api/v1/knowledge-contents")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List published knowledge or all content for content managers")
    public PageResponse<WorkspaceDtos.KnowledgeSummaryResponse> listKnowledge(
            Authentication authentication,
            @Valid @ParameterObject @ModelAttribute WorkspaceDtos.KnowledgeQuery query) {
        return workspaceService.listKnowledge(hasAuthority(authentication, "CONTENT_MANAGE"), query);
    }

    @GetMapping("/api/v1/knowledge-contents/statistics")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get visible knowledge statistics")
    public WorkspaceDtos.KnowledgeStatistics knowledgeStatistics(Authentication authentication) {
        return workspaceService.getKnowledgeStatistics(hasAuthority(authentication, "CONTENT_MANAGE"));
    }

    @GetMapping("/api/v1/knowledge-contents/{contentId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get visible knowledge content")
    public WorkspaceDtos.KnowledgeResponse getKnowledge(
            Authentication authentication, @PathVariable long contentId) {
        return workspaceService.getKnowledge(hasAuthority(authentication, "CONTENT_MANAGE"), contentId);
    }

    @PostMapping("/api/v1/knowledge-contents")
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    @Operation(summary = "Create draft knowledge content")
    public ResponseEntity<WorkspaceDtos.KnowledgeResponse> createKnowledge(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WorkspaceDtos.KnowledgeRequest request) {
        WorkspaceDtos.KnowledgeResponse response = workspaceService.createKnowledge(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/knowledge-contents/" + response.contentId())).body(response);
    }

    @PutMapping("/api/v1/knowledge-contents/{contentId}")
    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    @Operation(summary = "Update knowledge content without changing its status")
    public WorkspaceDtos.KnowledgeResponse updateKnowledge(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long contentId,
            @Valid @RequestBody WorkspaceDtos.KnowledgeRequest request) {
        return workspaceService.updateKnowledge(userId(jwt), contentId, request);
    }

    @GetMapping("/api/v1/work-tasks")
    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Operation(summary = "List accessible work tasks")
    public PageResponse<WorkspaceDtos.TaskSummaryResponse> listTasks(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ParameterObject @ModelAttribute WorkspaceDtos.TaskQuery query) {
        return workspaceService.listTasks(userId(jwt), query);
    }

    @GetMapping("/api/v1/work-tasks/{taskId}")
    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Operation(summary = "Get an accessible work task")
    public WorkspaceDtos.TaskResponse getTask(@AuthenticationPrincipal Jwt jwt, @PathVariable long taskId) {
        return workspaceService.getTask(userId(jwt), taskId);
    }

    @PostMapping("/api/v1/work-tasks")
    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Operation(summary = "Create a pending work task assigned to the current user")
    public ResponseEntity<WorkspaceDtos.TaskResponse> createTask(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WorkspaceDtos.TaskRequest request) {
        WorkspaceDtos.TaskResponse response = workspaceService.createTask(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/work-tasks/" + response.taskId())).body(response);
    }

    @PutMapping("/api/v1/work-tasks/{taskId}")
    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Operation(summary = "Update a work task without changing its status or assignee")
    public WorkspaceDtos.TaskResponse updateTask(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long taskId,
            @Valid @RequestBody WorkspaceDtos.TaskRequest request) {
        return workspaceService.updateTask(userId(jwt), taskId, request);
    }

    @GetMapping("/api/v1/dashboard")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get dashboard metrics for the current user")
    public WorkspaceDtos.DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return workspaceService.getDashboard(userId(jwt));
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(value -> authority.equals(value.getAuthority()));
    }
}
