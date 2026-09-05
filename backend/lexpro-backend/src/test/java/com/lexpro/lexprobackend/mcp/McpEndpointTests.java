package com.lexpro.lexprobackend.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.lexpro.lexprobackend.common.audit.AuditService;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "LEXPRO_DB_PASSWORD=test-only",
        "LEXPRO_JWT_SECRET=test-only-secret-that-is-at-least-32-bytes-long",
        "lexpro.mcp.enabled=true"
})
@AutoConfigureMockMvc
class McpEndpointTests {

    private static final String ACCEPT = "application/json, text/event-stream";
    private static final String TOKEN = "test-mcp-token-for-automated-tests-only-123456";

    @DynamicPropertySource
    static void mcpProperties(DynamicPropertyRegistry registry) {
        Path file = Path.of("src/test/resources/mcp-client-registry-test.json").toAbsolutePath().normalize();
        registry.add("lexpro.mcp.client-registry-path", file::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    @Test
    void shouldRequireMcpServiceAuthentication() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT, ACCEPT)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("MCP_AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectAnInvalidServiceToken() throws Exception {
        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token-value-that-is-long-enough-123456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT, ACCEPT)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("MCP_AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldNotAcceptMcpServiceTokensOnApplicationApis() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldInitializeAndListTheFourLegalAiTools() throws Exception {
        mockMvc.perform(authenticatedPost("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                          "protocolVersion":"2025-06-18",
                          "capabilities":{},
                          "clientInfo":{"name":"lexpro-test","version":"1.0"}
                        }}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.serverInfo.name").value("lexpro-legal-ai"))
                .andExpect(jsonPath("$.result.capabilities.tools").exists());

        mockMvc.perform(authenticatedPost("""
                        {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools.length()").value(4))
                .andExpect(jsonPath("$.result.tools[0].name").value("lexpro_recognize_legal_elements"))
                .andExpect(jsonPath("$.result.tools[3].name").value("lexpro_push_typical_cases"));
    }

    @Test
    void shouldReturnSanitizedJsonRpcErrorForMalformedMessage() throws Exception {
        mockMvc.perform(authenticatedPost("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.error.code").value(-32700))
                .andExpect(jsonPath("$.error.message").value("Invalid message format"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.cause").doesNotExist());
    }

    @Test
    void shouldRequireFactsForTypicalCaseRecommendation() throws Exception {
        mockMvc.perform(authenticatedPost("""
                        {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                          "name":"lexpro_push_typical_cases","arguments":{}
                        }}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(jsonPath("$.result.structuredContent.errorCode")
                        .value("MCP_INPUT_INVALID"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedPost(
            String body) {
        return post("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, ACCEPT)
                .content(body);
    }
}
