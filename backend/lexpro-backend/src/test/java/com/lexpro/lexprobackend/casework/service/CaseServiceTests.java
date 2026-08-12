package com.lexpro.lexprobackend.casework.service;

import com.lexpro.lexprobackend.casework.domain.CaseAssignment;
import com.lexpro.lexprobackend.casework.domain.CaseRecord;
import com.lexpro.lexprobackend.casework.mapper.CaseAssignmentMapper;
import com.lexpro.lexprobackend.casework.mapper.CaseRecordMapper;
import com.lexpro.lexprobackend.casework.web.dto.CreateCaseRequest;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CaseServiceTests {

    @Test
    void shouldCreatePendingCaseWithManagingAssignmentAndAudit() {
        CaseRecordMapper caseMapper = mock(CaseRecordMapper.class);
        CaseAssignmentMapper assignmentMapper = mock(CaseAssignmentMapper.class);
        AuditService auditService = mock(AuditService.class);
        when(caseMapper.existsByCaseNo("LEX-2026-1")).thenReturn(false);
        doAnswer(invocation -> {
            CaseRecord record = invocation.getArgument(0);
            record.setCaseId(41L);
            record.setCreatedAt(OffsetDateTime.parse("2026-07-29T10:00:00+08:00"));
            record.setUpdatedAt(record.getCreatedAt());
            return 1;
        }).when(caseMapper).insert(any(CaseRecord.class));
        when(caseMapper.selectById(41L)).thenAnswer(invocation -> {
            CaseRecord record = new CaseRecord();
            record.setCaseId(41L);
            record.setCaseName("Test case");
            record.setCaseNo("LEX-2026-1");
            record.setCaseType("CRIMINAL");
            record.setCreatorId(7L);
            record.setCaseStatus("PENDING");
            return record;
        });
        CaseService service = new CaseService(
                caseMapper, assignmentMapper, mock(CaseAccessService.class), auditService
        );

        service.createCase(7L, new CreateCaseRequest(
                " Test case ", " LEX-2026-1 ", "CRIMINAL", null, null, null,
                LocalDate.of(2026, 7, 29), OffsetDateTime.parse("2026-08-29T18:00:00+08:00")
        ));

        ArgumentCaptor<CaseRecord> caseCaptor = ArgumentCaptor.forClass(CaseRecord.class);
        verify(caseMapper).insert(caseCaptor.capture());
        assertEquals("PENDING", caseCaptor.getValue().getCaseStatus());
        assertEquals("LEX-2026-1", caseCaptor.getValue().getCaseNo());

        ArgumentCaptor<CaseAssignment> assignmentCaptor = ArgumentCaptor.forClass(CaseAssignment.class);
        verify(assignmentMapper).insert(assignmentCaptor.capture());
        assertEquals("ASSIGNEE", assignmentCaptor.getValue().getAssignmentRole());
        assertEquals("MANAGE", assignmentCaptor.getValue().getAccessLevel());
        assertEquals(7L, assignmentCaptor.getValue().getUserId());

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCaptor.capture());
        assertEquals("CASE_CREATED", auditCaptor.getValue().operationType());
    }

    @Test
    void shouldRejectDeadlineBeforeAcceptDate() {
        CaseRecordMapper caseMapper = mock(CaseRecordMapper.class);
        CaseAssignmentMapper assignmentMapper = mock(CaseAssignmentMapper.class);
        CaseService service = new CaseService(
                caseMapper, assignmentMapper, mock(CaseAccessService.class), mock(AuditService.class)
        );

        ApiException exception = Assertions.assertThrows(ApiException.class, () -> service.createCase(7L,
                new CreateCaseRequest(
                        "Test case", null, "CRIMINAL", null, null, null,
                        LocalDate.of(2026, 7, 29), OffsetDateTime.parse("2026-07-28T18:00:00+08:00")
                )));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("CASE_DEADLINE_INVALID", exception.getErrorCode());
        verifyNoInteractions(caseMapper, assignmentMapper);
    }

    @Test
    void shouldMapDuplicateCaseNumberToConflict() {
        CaseRecordMapper caseMapper = mock(CaseRecordMapper.class);
        CaseAssignmentMapper assignmentMapper = mock(CaseAssignmentMapper.class);
        when(caseMapper.existsByCaseNo("LEX-2026-1")).thenReturn(false);
        when(caseMapper.insert(any(CaseRecord.class))).thenThrow(new DuplicateKeyException("duplicate"));
        CaseService service = new CaseService(
                caseMapper, assignmentMapper, mock(CaseAccessService.class), mock(AuditService.class)
        );

        ApiException exception = Assertions.assertThrows(ApiException.class, () -> service.createCase(7L,
                new CreateCaseRequest(
                        "Test case", "LEX-2026-1", "CRIMINAL", null, null, null,
                        LocalDate.of(2026, 7, 29), null
                )));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("CASE_NUMBER_CONFLICT", exception.getErrorCode());
        verifyNoInteractions(assignmentMapper);
    }
}
