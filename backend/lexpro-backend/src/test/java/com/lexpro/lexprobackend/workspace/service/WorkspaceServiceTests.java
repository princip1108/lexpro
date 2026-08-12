package com.lexpro.lexprobackend.workspace.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.workspace.mapper.WorkspaceMapper;
import com.lexpro.lexprobackend.workspace.web.dto.WorkspaceDtos;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkspaceServiceTests {

    @Test
    void shouldRestrictOrdinaryKnowledgeQueriesToPublishedContent() {
        WorkspaceMapper mapper = mock(WorkspaceMapper.class);
        Page<WorkspaceMapper.KnowledgeRow> result = new Page<>(1, 20);
        result.setRecords(List.of(new WorkspaceMapper.KnowledgeRow(
                9L, "KNOWLEDGE", "Published item", null, null, "PUBLISHED", "User",
                OffsetDateTime.parse("2026-07-30T10:00:00+08:00"))));
        result.setTotal(1);
        when(mapper.selectKnowledgePage(any(), eq(true), eq(null), eq(null), eq(null))).thenReturn(result);
        WorkspaceService service = service(mapper);

        var response = service.listKnowledge(false, new WorkspaceDtos.KnowledgeQuery(null, null, null, null, null));

        assertEquals(1, response.totalItems());
        verify(mapper).selectKnowledgePage(any(), eq(true), eq(null), eq(null), eq(null));
    }

    @Test
    void shouldRejectTaskWithoutExactlyOneSubject() {
        WorkspaceMapper mapper = mock(WorkspaceMapper.class);
        WorkspaceService service = service(mapper);
        WorkspaceDtos.TaskRequest request = new WorkspaceDtos.TaskRequest(
                null, null, "REVIEW", "Review material", null, "MEDIUM", null);

        ApiException exception = assertThrows(ApiException.class, () -> service.createTask(7L, request));

        assertEquals("WORK_TASK_SUBJECT_INVALID", exception.getErrorCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldAcceptTextKnowledgeWhenJsonFieldIsExplicitNull() {
        WorkspaceMapper mapper = mock(WorkspaceMapper.class);
        when(mapper.insertKnowledge(any())).thenAnswer(invocation -> {
            invocation.<com.lexpro.lexprobackend.workspace.domain.KnowledgeContent>getArgument(0).setContentId(12L);
            return 1;
        });
        when(mapper.selectKnowledge(12L)).thenReturn(new WorkspaceMapper.KnowledgeDetailRow(
                12L, "RULE", "Acceptance rule", "Fictional text", null, null, null,
                "DRAFT", 7L, "Admin", null, null,
                OffsetDateTime.parse("2026-07-31T23:30:00+08:00"),
                OffsetDateTime.parse("2026-07-31T23:30:00+08:00")));
        WorkspaceService service = service(mapper);

        var response = service.createKnowledge(7L, new WorkspaceDtos.KnowledgeRequest(
                "RULE", "Acceptance rule", "Fictional text", NullNode.getInstance(), null));

        assertEquals("DRAFT", response.status());
        assertEquals(null, response.contentJson());
    }

    private WorkspaceService service(WorkspaceMapper mapper) {
        return new WorkspaceService(mapper, mock(CaseAccessService.class), mock(AuditService.class), new ObjectMapper());
    }
}
