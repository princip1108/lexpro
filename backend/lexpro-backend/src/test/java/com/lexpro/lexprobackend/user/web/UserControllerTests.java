package com.lexpro.lexprobackend.user.web;

import com.lexpro.lexprobackend.user.service.AppUserService;
import com.lexpro.lexprobackend.user.service.UserAdministrationService;
import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import com.lexpro.lexprobackend.common.web.dto.PageRequest;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import com.lexpro.lexprobackend.user.web.dto.UserDetailResponse;
import com.lexpro.lexprobackend.user.web.dto.UserSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserService appUserService;

    @MockitoBean
    private UserAdministrationService userAdministrationService;

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
        when(appUserService.listUsers(any(PageRequest.class))).thenReturn(
                new PageResponse<>(List.of(user), 1, 20, 1, 1)
        );

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].userId").value(1))
                .andExpect(jsonPath("$.items[0].username").value("admin"))
                .andExpect(jsonPath("$.items[0].realName").value("System Administrator"))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void shouldReturnUserDetailsWithoutCredentialData() throws Exception {
        UserDetailResponse user = new UserDetailResponse(
                1L,
                "admin",
                "System Administrator",
                "ACTIVE",
                new UserDetailResponse.OrganizationSummary(2L, "LEXPRO", "LexPro"),
                new UserDetailResponse.RoleSummary(3L, "ADMIN", "System administrator"),
                List.of("USER_MANAGE"),
                OffsetDateTime.parse("2026-07-28T00:00:00+08:00"),
                OffsetDateTime.parse("2026-07-28T00:00:00+08:00")
        );
        when(appUserService.getUser(1L)).thenReturn(user);

        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.organization.code").value("LEXPRO"))
                .andExpect(jsonPath("$.role.code").value("ADMIN"))
                .andExpect(jsonPath("$.permissions[0]").value("USER_MANAGE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldReturnProblemDetailForInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .queryParam("page", "0")
                        .queryParam("size", "101")
                        .header(RequestIdFilter.HEADER_NAME, "test-request-123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.page").value("must be at least 1"))
                .andExpect(jsonPath("$.fieldErrors.size").value("must be at most 100"));
    }

    @Test
    void shouldAllowConfiguredVueDevelopmentOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/users")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void shouldRejectUnknownCorsOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/users")
                        .header("Origin", "https://example.invalid")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
