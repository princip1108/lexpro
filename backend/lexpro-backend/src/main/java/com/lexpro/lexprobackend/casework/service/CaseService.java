package com.lexpro.lexprobackend.casework.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexpro.lexprobackend.casework.domain.CaseAssignment;
import com.lexpro.lexprobackend.casework.domain.CaseRecord;
import com.lexpro.lexprobackend.casework.mapper.CaseAssignmentMapper;
import com.lexpro.lexprobackend.casework.mapper.CaseRecordMapper;
import com.lexpro.lexprobackend.casework.mapper.CaseSummaryRow;
import com.lexpro.lexprobackend.casework.web.dto.CaseDetailResponse;
import com.lexpro.lexprobackend.casework.web.dto.CaseQuery;
import com.lexpro.lexprobackend.casework.web.dto.CaseSummaryResponse;
import com.lexpro.lexprobackend.casework.web.dto.CreateCaseRequest;
import com.lexpro.lexprobackend.casework.web.dto.UpdateCaseRequest;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.common.web.dto.PageRequest;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
public class CaseService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final CaseRecordMapper caseRecordMapper;
    private final CaseAssignmentMapper caseAssignmentMapper;
    private final CaseAccessService caseAccessService;
    private final AuditService auditService;

    public CaseService(CaseRecordMapper caseRecordMapper, CaseAssignmentMapper caseAssignmentMapper,
                       CaseAccessService caseAccessService, AuditService auditService) {
        this.caseRecordMapper = caseRecordMapper;
        this.caseAssignmentMapper = caseAssignmentMapper;
        this.caseAccessService = caseAccessService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CaseSummaryResponse> listCases(long userId, CaseQuery query) {
        PageRequest request = query.pageRequest();
        Page<CaseSummaryRow> page = caseRecordMapper.selectVisiblePage(
                new Page<>(request.page(), request.size()), userId, trimToNull(query.keyword()),
                query.status(), trimToNull(query.caseType()), query.overdue());
        return PageResponse.from(page, CaseSummaryRow::toResponse);
    }

    @Transactional(readOnly = true)
    public CaseDetailResponse getCase(long userId, long caseId) {
        return response(caseAccessService.requireRead(caseId, userId));
    }

    @Transactional
    public CaseDetailResponse createCase(long userId, CreateCaseRequest request) {
        validateDates(request.acceptDate(), request.deadlineAt());
        String caseNo = trimToNull(request.caseNo());
        requireCaseNoAvailable(caseNo, null);

        CaseRecord record = new CaseRecord();
        apply(record, request.caseName(), caseNo, request.caseType(), request.caseCause(), request.caseSource(),
                request.currentStage(), request.acceptDate(), request.deadlineAt());
        record.setCreatorId(userId);
        record.setCaseStatus("PENDING");
        try {
            caseRecordMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            throw caseNumberConflict();
        }

        CaseAssignment assignment = new CaseAssignment();
        assignment.setCaseId(record.getCaseId());
        assignment.setUserId(userId);
        assignment.setAssignmentRole("ASSIGNEE");
        assignment.setAccessLevel("MANAGE");
        assignment.setAssignedAt(OffsetDateTime.now());
        assignment.setAssignedBy(userId);
        caseAssignmentMapper.insert(assignment);

        auditService.record(new AuditEvent(userId, record.getCaseId(), "CASE_CREATED", "CASE",
                String.valueOf(record.getCaseId()), AuditResult.SUCCESS,
                Map.of("caseName", record.getCaseName(), "initialStatus", "PENDING")));
        return response(caseRecordMapper.selectById(record.getCaseId()));
    }

    @Transactional
    public CaseDetailResponse updateCase(long userId, long caseId, UpdateCaseRequest request) {
        CaseRecord record = caseAccessService.requireEdit(caseId, userId);
        validateDates(request.acceptDate(), request.deadlineAt());
        String caseNo = trimToNull(request.caseNo());
        requireCaseNoAvailable(caseNo, caseId);
        apply(record, request.caseName(), caseNo, request.caseType(), request.caseCause(), request.caseSource(),
                request.currentStage(), request.acceptDate(), request.deadlineAt());
        try {
            caseRecordMapper.updateMetadata(record);
        } catch (DuplicateKeyException exception) {
            throw caseNumberConflict();
        }
        auditService.record(new AuditEvent(userId, caseId, "CASE_UPDATED", "CASE", String.valueOf(caseId),
                AuditResult.SUCCESS, Map.of("caseName", record.getCaseName())));
        return response(caseRecordMapper.selectById(caseId));
    }

    private void apply(CaseRecord record, String caseName, String caseNo, String caseType, String caseCause,
                       String caseSource, String currentStage, LocalDate acceptDate, OffsetDateTime deadlineAt) {
        record.setCaseName(caseName.trim());
        record.setCaseNo(caseNo);
        record.setCaseType(caseType.trim());
        record.setCaseCause(trimToNull(caseCause));
        record.setCaseSource(trimToNull(caseSource));
        record.setCurrentStage(trimToNull(currentStage));
        record.setAcceptDate(acceptDate);
        record.setDeadlineAt(deadlineAt);
    }

    private void validateDates(LocalDate acceptDate, OffsetDateTime deadlineAt) {
        if (acceptDate != null && deadlineAt != null
                && deadlineAt.isBefore(acceptDate.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid case deadline", "CASE_DEADLINE_INVALID",
                    "deadlineAt must not be earlier than acceptDate");
        }
    }

    private void requireCaseNoAvailable(String caseNo, Long existingCaseId) {
        if (caseNo == null || !caseRecordMapper.existsByCaseNo(caseNo)) return;
        if (existingCaseId != null) {
            CaseRecord existing = caseRecordMapper.selectById(existingCaseId);
            if (existing != null && caseNo.equals(existing.getCaseNo())) return;
        }
        throw caseNumberConflict();
    }

    private ApiException caseNumberConflict() {
        return new ApiException(HttpStatus.CONFLICT, "Case number already exists", "CASE_NUMBER_CONFLICT",
                "Another case already uses this case number");
    }

    private CaseDetailResponse response(CaseRecord record) {
        boolean overdue = record.getDeadlineAt() != null && record.getDeadlineAt().isBefore(OffsetDateTime.now())
                && !"CLOSED".equals(record.getCaseStatus()) && !"ARCHIVED".equals(record.getCaseStatus());
        return CaseDetailResponse.from(record, overdue);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
