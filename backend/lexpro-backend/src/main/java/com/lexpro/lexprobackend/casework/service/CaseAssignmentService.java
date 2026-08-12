package com.lexpro.lexprobackend.casework.service;

import com.lexpro.lexprobackend.casework.domain.CaseAssignment;
import com.lexpro.lexprobackend.casework.mapper.CaseAssignmentMapper;
import com.lexpro.lexprobackend.casework.web.dto.CaseAssignmentResponse;
import com.lexpro.lexprobackend.casework.web.dto.CreateCaseAssignmentRequest;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.user.domain.UserAccount;
import com.lexpro.lexprobackend.user.service.AppUserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class CaseAssignmentService {

    private final CaseAssignmentMapper caseAssignmentMapper;
    private final CaseAccessService caseAccessService;
    private final AppUserService appUserService;
    private final AuditService auditService;

    public CaseAssignmentService(CaseAssignmentMapper caseAssignmentMapper, CaseAccessService caseAccessService,
                                 AppUserService appUserService, AuditService auditService) {
        this.caseAssignmentMapper = caseAssignmentMapper;
        this.caseAccessService = caseAccessService;
        this.appUserService = appUserService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CaseAssignmentResponse> history(long userId, long caseId) {
        caseAccessService.requireRead(caseId, userId);
        return List.copyOf(caseAssignmentMapper.selectHistory(caseId));
    }

    @Transactional
    public CaseAssignmentResponse assign(long actorId, long caseId, CreateCaseAssignmentRequest request) {
        caseAccessService.requireManage(caseId, actorId);
        UserAccount account = appUserService.findAccountById(request.userId());
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "User is not active", "CASE_ASSIGNEE_INACTIVE",
                    "Only active users can receive a case assignment");
        }
        CaseAssignment assignment = new CaseAssignment();
        assignment.setCaseId(caseId);
        assignment.setUserId(request.userId());
        assignment.setAssignmentRole(request.assignmentRole());
        assignment.setAccessLevel(request.accessLevel());
        assignment.setAssignedAt(OffsetDateTime.now());
        assignment.setAssignedBy(actorId);
        try {
            caseAssignmentMapper.insert(assignment);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Assignment already exists", "CASE_ASSIGNMENT_CONFLICT",
                    "The user already has a current assignment with this role");
        }
        audit(actorId, caseId, assignment, "CASE_ASSIGNED");
        return findResponse(caseId, assignment.getAssignmentId());
    }

    @Transactional
    public CaseAssignmentResponse end(long actorId, long caseId, long assignmentId) {
        caseAccessService.requireManage(caseId, actorId);
        CaseAssignment assignment = caseAssignmentMapper.selectById(assignmentId);
        if (assignment == null || assignment.getCaseId() != caseId) {
            throw notFound();
        }
        if (assignment.getEndedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Assignment already ended", "CASE_ASSIGNMENT_ENDED",
                    "The assignment has already ended");
        }
        assignment.setEndedAt(OffsetDateTime.now());
        caseAssignmentMapper.updateById(assignment);
        audit(actorId, caseId, assignment, "CASE_ASSIGNMENT_ENDED");
        return findResponse(caseId, assignmentId);
    }

    private CaseAssignmentResponse findResponse(long caseId, long assignmentId) {
        return caseAssignmentMapper.selectHistory(caseId).stream()
                .filter(item -> item.getAssignmentId() == assignmentId)
                .findFirst().orElseThrow(this::notFound);
    }

    private void audit(long actorId, long caseId, CaseAssignment assignment, String type) {
        auditService.record(new AuditEvent(actorId, caseId, type, "CASE_ASSIGNMENT",
                String.valueOf(assignment.getAssignmentId()), AuditResult.SUCCESS,
                Map.of("userId", assignment.getUserId(), "role", assignment.getAssignmentRole(),
                        "accessLevel", assignment.getAccessLevel())));
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Assignment not found", "CASE_ASSIGNMENT_NOT_FOUND",
                "The requested case assignment does not exist");
    }
}
