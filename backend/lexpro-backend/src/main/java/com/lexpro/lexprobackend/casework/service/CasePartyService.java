package com.lexpro.lexprobackend.casework.service;

import com.lexpro.lexprobackend.casework.domain.CaseParty;
import com.lexpro.lexprobackend.casework.mapper.CasePartyMapper;
import com.lexpro.lexprobackend.casework.web.dto.CasePartyRequest;
import com.lexpro.lexprobackend.casework.web.dto.CasePartyResponse;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class CasePartyService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final CasePartyMapper casePartyMapper;
    private final CaseAccessService caseAccessService;
    private final AuditService auditService;

    public CasePartyService(CasePartyMapper casePartyMapper, CaseAccessService caseAccessService,
                            AuditService auditService) {
        this.casePartyMapper = casePartyMapper;
        this.caseAccessService = caseAccessService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CasePartyResponse> list(long userId, long caseId) {
        caseAccessService.requireRead(caseId, userId);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return casePartyMapper.selectSafeByCaseId(caseId).stream()
                .map(party -> CasePartyResponse.from(party, today)).toList();
    }

    @Transactional
    public CasePartyResponse create(long userId, long caseId, CasePartyRequest request) {
        caseAccessService.requireEdit(caseId, userId);
        CaseParty party = new CaseParty();
        party.setCaseId(caseId);
        apply(party, request);
        casePartyMapper.insert(party);
        audit(userId, caseId, party, "CASE_PARTY_CREATED");
        return response(casePartyMapper.selectSafeById(caseId, party.getPartyId()));
    }

    @Transactional
    public CasePartyResponse update(long userId, long caseId, long partyId, CasePartyRequest request) {
        caseAccessService.requireEdit(caseId, userId);
        CaseParty party = requireParty(caseId, partyId);
        apply(party, request);
        casePartyMapper.updateSafeFields(party);
        audit(userId, caseId, party, "CASE_PARTY_UPDATED");
        return response(casePartyMapper.selectSafeById(caseId, partyId));
    }

    private CaseParty requireParty(long caseId, long partyId) {
        CaseParty party = casePartyMapper.selectSafeById(caseId, partyId);
        if (party == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Case party not found", "CASE_PARTY_NOT_FOUND",
                    "The requested case party does not exist");
        }
        return party;
    }

    private void apply(CaseParty party, CasePartyRequest request) {
        party.setPartyName(request.partyName().trim());
        party.setPartyRole(request.partyRole());
        party.setPartyType(request.partyType());
        party.setIdentityType(trimToNull(request.identityType()));
        party.setGender(trimToNull(request.gender()));
        party.setBirthDate(request.birthDate());
        party.setDescription(trimToNull(request.description()));
    }

    private CasePartyResponse response(CaseParty party) {
        return CasePartyResponse.from(party, LocalDate.now(BUSINESS_ZONE));
    }

    private void audit(long userId, long caseId, CaseParty party, String type) {
        auditService.record(new AuditEvent(userId, caseId, type, "CASE_PARTY", String.valueOf(party.getPartyId()),
                AuditResult.SUCCESS, Map.of("partyRole", party.getPartyRole(), "partyType", party.getPartyType())));
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
