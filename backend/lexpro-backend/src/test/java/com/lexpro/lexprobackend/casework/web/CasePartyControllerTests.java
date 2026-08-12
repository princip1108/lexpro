package com.lexpro.lexprobackend.casework.web;

import com.lexpro.lexprobackend.casework.service.CasePartyService;
import com.lexpro.lexprobackend.casework.web.dto.CasePartyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CasePartyController.class)
@AutoConfigureMockMvc
class CasePartyControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CasePartyService casePartyService;

    @Test
    void shouldNotSerializeSensitiveIdentityFields() throws Exception {
        when(casePartyService.list(7L, 9L)).thenReturn(List.of(new CasePartyResponse(
                5L, 9L, "Zhang", "SUSPECT", "PERSON", "CN_ID", "310***********1234",
                null, null, null, null, null, null
        )));

        mockMvc.perform(get("/api/v1/cases/9/parties").with(jwt()
                        .jwt(token -> token.subject("7"))
                        .authorities(new SimpleGrantedAuthority("CASE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].identityNumberMasked").value("310***********1234"))
                .andExpect(jsonPath("$[0].identityNumber").doesNotExist())
                .andExpect(jsonPath("$[0].identityNumberHash").doesNotExist());
    }
}
