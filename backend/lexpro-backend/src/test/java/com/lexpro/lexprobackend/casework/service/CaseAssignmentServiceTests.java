package com.lexpro.lexprobackend.casework.service;

import com.lexpro.lexprobackend.casework.domain.CaseAssignment;
import com.lexpro.lexprobackend.casework.mapper.CaseAssignmentMapper;
import com.lexpro.lexprobackend.casework.web.dto.CaseAssignmentResponse;
import com.lexpro.lexprobackend.casework.web.dto.CreateCaseAssignmentRequest;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.user.domain.UserAccount;
import com.lexpro.lexprobackend.user.service.AppUserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CaseAssignmentServiceTests {

    @Test
    void shouldAssignActiveUserAndAudit() {
        CaseAssignmentMapper mapper = mock(CaseAssignmentMapper.class);
        AppUserService appUserService = mock(AppUserService.class);
        AuditService auditService = mock(AuditService.class);
        UserAccount account = activeUser(9L);
        when(appUserService.findAccountById(9L)).thenReturn(account);
        doAnswer(invocation -> {
            CaseAssignment assignment = invocation.getArgument(0);
            assignment.setAssignmentId(88L);
            return 1;
        }).when(mapper).insert(any(CaseAssignment.class));
        when(mapper.selectHistory(41L)).thenReturn(List.of(response(88L, 41L, 9L, null)));
        CaseAssignmentService service = new CaseAssignmentService(
                mapper, mock(CaseAccessService.class), appUserService, auditService
        );

        CaseAssignmentResponse response = service.assign(7L, 41L,
                new CreateCaseAssignmentRequest(9L, "REVIEWER", "EDIT"));

        assertEquals(88L, response.getAssignmentId());
        ArgumentCaptor<CaseAssignment> assignmentCaptor = ArgumentCaptor.forClass(CaseAssignment.class);
        verify(mapper).insert(assignmentCaptor.capture());
        assertEquals("REVIEWER", assignmentCaptor.getValue().getAssignmentRole());
        assertEquals("EDIT", assignmentCaptor.getValue().getAccessLevel());
        assertEquals(7L, assignmentCaptor.getValue().getAssignedBy());

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCaptor.capture());
        assertEquals("CASE_ASSIGNED", auditCaptor.getValue().operationType());
    }

    @Test
    void shouldRejectInactiveAssignee() {
        CaseAssignmentMapper mapper = mock(CaseAssignmentMapper.class);
        AppUserService appUserService = mock(AppUserService.class);
        UserAccount account = activeUser(9L);
        account.setStatus("DISABLED");
        when(appUserService.findAccountById(9L)).thenReturn(account);
        CaseAssignmentService service = new CaseAssignmentService(
                mapper, mock(CaseAccessService.class), appUserService, mock(AuditService.class)
        );

        ApiException exception = Assertions.assertThrows(ApiException.class, () -> service.assign(7L, 41L,
                new CreateCaseAssignmentRequest(9L, "REVIEWER", "EDIT")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("CASE_ASSIGNEE_INACTIVE", exception.getErrorCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldEndCurrentAssignmentAndAudit() {
        CaseAssignmentMapper mapper = mock(CaseAssignmentMapper.class);
        AuditService auditService = mock(AuditService.class);
        CaseAssignment assignment = new CaseAssignment();
        assignment.setAssignmentId(88L);
        assignment.setCaseId(41L);
        assignment.setUserId(9L);
        assignment.setAssignmentRole("REVIEWER");
        assignment.setAccessLevel("EDIT");
        when(mapper.selectById(88L)).thenReturn(assignment);
        when(mapper.selectHistory(41L)).thenReturn(List.of(response(88L, 41L, 9L, assignment.getEndedAt())));
        CaseAssignmentService service = new CaseAssignmentService(
                mapper, mock(CaseAccessService.class), mock(AppUserService.class), auditService
        );

        service.end(7L, 41L, 88L);

        ArgumentCaptor<CaseAssignment> assignmentCaptor = ArgumentCaptor.forClass(CaseAssignment.class);
        verify(mapper).updateById(assignmentCaptor.capture());
        Assertions.assertNotNull(assignmentCaptor.getValue().getEndedAt());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCaptor.capture());
        assertEquals("CASE_ASSIGNMENT_ENDED", auditCaptor.getValue().operationType());
    }

    @Test
    void shouldHideInaccessibleAssignmentAsNotFound() {
        CaseAssignmentMapper mapper = mock(CaseAssignmentMapper.class);
        CaseAssignment assignment = new CaseAssignment();
        assignment.setAssignmentId(88L);
        assignment.setCaseId(42L);
        when(mapper.selectById(88L)).thenReturn(assignment);
        CaseAssignmentService service = new CaseAssignmentService(
                mapper, mock(CaseAccessService.class), mock(AppUserService.class), mock(AuditService.class)
        );

        ApiException exception = Assertions.assertThrows(ApiException.class, () -> service.end(7L, 41L, 88L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("CASE_ASSIGNMENT_NOT_FOUND", exception.getErrorCode());
    }

    private UserAccount activeUser(long userId) {
        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setStatus("ACTIVE");
        return account;
    }

    private CaseAssignmentResponse response(long assignmentId, long caseId, long userId, java.time.OffsetDateTime endedAt) {
        CaseAssignmentResponse response = new CaseAssignmentResponse();
        response.setAssignmentId(assignmentId);
        response.setCaseId(caseId);
        response.setUserId(userId);
        response.setAssignmentRole("REVIEWER");
        response.setAccessLevel("EDIT");
        response.setEndedAt(endedAt);
        return response;
    }
}
