package com.lexpro.lexprobackend.casework.service;

import com.lexpro.lexprobackend.casework.domain.CaseParty;
import com.lexpro.lexprobackend.casework.mapper.CasePartyMapper;
import com.lexpro.lexprobackend.casework.web.dto.CasePartyResponse;
import com.lexpro.lexprobackend.common.audit.AuditService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CasePartyServiceTests {

    @Test
    void shouldExposeOnlyMaskedIdentityValue() {
        CasePartyMapper mapper = mock(CasePartyMapper.class);
        CaseParty party = new CaseParty();
        party.setPartyId(5L);
        party.setCaseId(9L);
        party.setPartyName("Zhang");
        party.setPartyRole("SUSPECT");
        party.setPartyType("PERSON");
        party.setIdentityType("CN_ID");
        party.setIdentityNumber("ciphertext-must-not-leak");
        party.setIdentityNumberHash("hash-must-not-leak");
        party.setIdentityNumberMasked("310***********1234");
        party.setBirthDate(LocalDate.now().minusYears(30));
        when(mapper.selectSafeByCaseId(9L)).thenReturn(List.of(party));
        CasePartyService service = new CasePartyService(
                mapper, mock(CaseAccessService.class), mock(AuditService.class)
        );

        List<CasePartyResponse> response = service.list(3L, 9L);

        assertEquals("310***********1234", response.getFirst().identityNumberMasked());
        assertEquals(30, response.getFirst().age());
    }
}
