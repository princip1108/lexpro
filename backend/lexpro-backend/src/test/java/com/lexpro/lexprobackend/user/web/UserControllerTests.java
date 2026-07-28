package com.lexpro.lexprobackend.user.web;

import com.lexpro.lexprobackend.user.service.AppUserService;
import com.lexpro.lexprobackend.user.web.dto.UserSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    void shouldReturnUserSummariesWithoutPassword() throws Exception {
        UserSummaryResponse user = new UserSummaryResponse(
                1L,
                "admin",
                "System Administrator",
                "ACTIVE",
                null,
                1L,
                OffsetDateTime.parse("2026-07-28T00:00:00+08:00")
        );
        when(appUserService.listUsers()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(1))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].realName").value("System Administrator"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }
}
