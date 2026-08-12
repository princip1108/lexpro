package com.lexpro.lexprobackend.casework.service;

import com.lexpro.lexprobackend.casework.domain.CaseRecord;
import com.lexpro.lexprobackend.casework.mapper.CaseRecordMapper;
import com.lexpro.lexprobackend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseAccessService {

    private final CaseRecordMapper caseRecordMapper;

    public CaseAccessService(CaseRecordMapper caseRecordMapper) {
        this.caseRecordMapper = caseRecordMapper;
    }

    @Transactional(readOnly = true)
    public CaseRecord requireRead(long caseId, long userId) { return requireAccess(caseId, userId, 1); }

    @Transactional(readOnly = true)
    public CaseRecord requireEdit(long caseId, long userId) { return requireAccess(caseId, userId, 2); }

    @Transactional(readOnly = true)
    public CaseRecord requireManage(long caseId, long userId) { return requireAccess(caseId, userId, 3); }

    private CaseRecord requireAccess(long caseId, long userId, int requiredRank) {
        Integer rank = caseRecordMapper.selectAccessRank(caseId, userId);
        if (rank == null || rank < requiredRank) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Case not found", "CASE_NOT_FOUND",
                    "The requested case does not exist or is not accessible");
        }
        CaseRecord record = caseRecordMapper.selectById(caseId);
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Case not found", "CASE_NOT_FOUND",
                    "The requested case does not exist");
        }
        return record;
    }
}
