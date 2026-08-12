package com.lexpro.lexprobackend.casework.service;

import com.lexpro.lexprobackend.casework.mapper.CaseRecordMapper;
import com.lexpro.lexprobackend.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseAccessServiceTests {

    @Test
    void shouldHideCasesWithoutRequiredCaseLevelAccess() {
        CaseRecordMapper mapper = mock(CaseRecordMapper.class);
        when(mapper.selectAccessRank(12L, 7L)).thenReturn(1);
        CaseAccessService service = new CaseAccessService(mapper);

        ApiException exception = assertThrows(ApiException.class, () -> service.requireEdit(12L, 7L));

        assertEquals("CASE_NOT_FOUND", exception.getErrorCode());
    }
}
