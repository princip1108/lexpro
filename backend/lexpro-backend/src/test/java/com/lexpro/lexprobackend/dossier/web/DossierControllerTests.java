package com.lexpro.lexprobackend.dossier.web;

import com.lexpro.lexprobackend.auth.config.JwtProperties;
import com.lexpro.lexprobackend.auth.config.SecurityConfig;
import com.lexpro.lexprobackend.auth.config.SecurityProblemWriter;
import com.lexpro.lexprobackend.dossier.service.DossierCatalogService;
import com.lexpro.lexprobackend.dossier.service.DossierFileService;
import com.lexpro.lexprobackend.dossier.web.dto.DossierFileResponse;
import com.lexpro.lexprobackend.user.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DossierController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, SecurityProblemWriter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "lexpro.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long",
        "lexpro.jwt.issuer=https://lexpro.local",
        "lexpro.jwt.access-token-ttl=PT30M"
})
class DossierControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DossierCatalogService catalogService;

    @MockitoBean
    private DossierFileService fileService;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    void shouldListSafeMetadataWithoutInternalStorageLocation() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        when(fileService.list(7L, 9L, null, false)).thenReturn(List.of(new DossierFileResponse(
                5L, 9L, null, "evidence.pdf", "application/pdf", 7L, "Uploader", now,
                123L, "sha256", "ACTIVE", now, null, List.of()
        )));

        mockMvc.perform(get("/api/v1/cases/9/dossier/files").with(jwt()
                        .jwt(token -> token.subject("7"))
                        .authorities(new SimpleGrantedAuthority("CASE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("evidence.pdf"))
                .andExpect(jsonPath("$[0].fileHash").value("sha256"))
                .andExpect(jsonPath("$[0].fileUrl").doesNotExist())
                .andExpect(jsonPath("$[0].storagePath").doesNotExist());
    }

    @Test
    void shouldRequireDossierManagePermissionForUpload() throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/cases/9/dossier/files")
                .file("file", "content".getBytes());

        mockMvc.perform(request.with(jwt()
                        .jwt(token -> token.subject("7"))
                        .authorities(new SimpleGrantedAuthority("CASE_READ"))))
                .andExpect(status().isForbidden());
    }
}
