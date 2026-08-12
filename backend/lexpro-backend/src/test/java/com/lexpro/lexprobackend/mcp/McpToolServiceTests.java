package com.lexpro.lexprobackend.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.mcp.config.McpProperties;
import com.lexpro.lexprobackend.mcp.service.McpInvocationLimiter;
import com.lexpro.lexprobackend.mcp.service.McpToolCatalog;
import com.lexpro.lexprobackend.mcp.service.McpToolService;
import com.lexpro.lexprobackend.processing.ai.CaseSummaryClient;
import com.lexpro.lexprobackend.processing.ai.CaseSummaryOutput;
import com.lexpro.lexprobackend.processing.ai.EntityRecognitionClient;
import com.lexpro.lexprobackend.processing.ai.EntityRecognitionOutput;
import com.lexpro.lexprobackend.processing.ai.LegalElementRecognitionClient;
import com.lexpro.lexprobackend.processing.ai.LegalElementRecognitionOutput;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpToolServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private LegalElementRecognitionClient legalElementClient;
    private EntityRecognitionClient entityClient;
    private CaseSummaryClient summaryClient;
    private AuditService auditService;
    private McpProperties mcpProperties;
    private AiProcessingProperties aiProperties;
    private McpToolService service;

    @BeforeEach
    void setUp() {
        legalElementClient = mock(LegalElementRecognitionClient.class);
        entityClient = mock(EntityRecognitionClient.class);
        summaryClient = mock(CaseSummaryClient.class);
        auditService = mock(AuditService.class);
        mcpProperties = new McpProperties();
        aiProperties = new AiProcessingProperties();
        aiProperties.setEnabled(true);
        aiProperties.setAllowExternalCaseData(true);
        service = new McpToolService(legalElementClient, entityClient, summaryClient, aiProperties,
                new McpInvocationLimiter(mcpProperties), auditService, objectMapper, mcpProperties);
    }

    @Test
    void shouldExposeOnlyTheFourLegalAiTools() {
        McpToolCatalog catalog = new McpToolCatalog(service, mcpProperties, aiProperties);

        var tools = catalog.tools().stream().map(specification -> specification.tool()).toList();

        assertEquals(List.of(
                "lexpro_recognize_legal_elements",
                "lexpro_recognize_entities",
                "lexpro_summarize_case",
                "lexpro_push_typical_cases"
        ), tools.stream().map(McpSchema.Tool::name).toList());
        assertTrue(tools.stream().allMatch(tool -> Boolean.TRUE.equals(tool.annotations().readOnlyHint())));
        assertTrue(tools.stream().allMatch(tool -> Boolean.FALSE.equals(tool.annotations().destructiveHint())));
        assertTrue(tools.stream().allMatch(tool -> Boolean.FALSE.equals(tool.inputSchema().additionalProperties())));
    }

    @Test
    void shouldReturnAllowlistedLegalElementResultWithoutPromptMetadata() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {"caseCause":"合同纠纷","elements":[{"code":"FACT","name":"事实","content":"已付款",
                "satisfied":true,"confidence":0.9,"evidence":[{"quote":"已付款","startOffset":0,"endOffset":3}]}],
                "validation":{"warnings":[],"unsupportedClaims":[]}}
                """);
        when(legalElementClient.recognize("已付款五万元", null, "mcp-test-request")).thenReturn(
                new LegalElementRecognitionOutput(root, root.get("validation"), "合同纠纷", "deepseek-test",
                        "prompt-v1", "legal-elements-v1", "private prompt", null, null));

        McpSchema.CallToolResult result = service.recognizeLegalElements(context(Set.of("AI_EXECUTE")),
                new McpSchema.CallToolRequest("lexpro_recognize_legal_elements", Map.of("text", "已付款五万元")));

        assertFalse(result.isError());
        assertTrue(text(result).contains("合同纠纷"));
        assertTrue(text(result).contains("deepseek-test"));
        assertFalse(text(result).contains("private prompt"));
        assertFalse(text(result).contains("promptVersion"));
        verify(auditService).recordIndependent(any(), eq("mcp-test-request"));
    }

    @Test
    void shouldRejectMissingPermissionAndUnknownArgumentsBeforeCallingAi() {
        McpSchema.CallToolResult denied = service.recognizeEntities(context(Set.of()),
                new McpSchema.CallToolRequest("lexpro_recognize_entities", Map.of("text", "张三")));
        McpSchema.CallToolResult unknown = service.recognizeEntities(context(Set.of("AI_EXECUTE")),
                new McpSchema.CallToolRequest("lexpro_recognize_entities",
                        Map.of("text", "张三", "url", "https://internal.example")));

        assertTrue(denied.isError());
        assertTrue(text(denied).contains("MCP_ACCESS_DENIED"));
        assertTrue(unknown.isError());
        assertTrue(text(unknown).contains("MCP_INPUT_INVALID"));
        verify(entityClient, never()).recognize(any(), any());
    }

    @Test
    void shouldReturnEntitiesAndSummaryUsingSafeResponseShapes() throws Exception {
        JsonNode entities = objectMapper.readTree("""
                {"entities":[{"type":"PERSON","text":"张三","startOffset":0,"endOffset":2,"confidence":1.0}]}
                """);
        when(entityClient.recognize("张三提交证据", "mcp-test-request")).thenReturn(
                new EntityRecognitionOutput(entities, "deepseek-test", "prompt-v1", "entity-result-v1",
                        "private prompt", null, null));
        when(summaryClient.summarize(eq("FACT"), any(), eq("mcp-test-request"))).thenReturn(
                new CaseSummaryOutput("张三提交了证据。", "deepseek-test", "prompt-v1", "case-summary-v1",
                        "private prompt", null, null));

        McpSchema.CallToolResult entityResult = service.recognizeEntities(context(Set.of("AI_EXECUTE")),
                new McpSchema.CallToolRequest("lexpro_recognize_entities", Map.of("text", "张三提交证据")));
        McpSchema.CallToolResult summaryResult = service.summarizeCase(context(Set.of("AI_EXECUTE")),
                new McpSchema.CallToolRequest("lexpro_summarize_case", Map.of(
                        "summaryType", "FACT",
                        "documents", List.of(Map.of("documentId", "doc-a", "text", "张三提交证据")))));

        assertFalse(entityResult.isError());
        assertTrue(text(entityResult).contains("PERSON"));
        assertFalse(summaryResult.isError());
        assertTrue(text(summaryResult).contains("doc-a"));
        assertTrue(text(summaryResult).contains("张三提交了证据"));
        assertFalse(text(summaryResult).contains("private prompt"));
    }

    @Test
    void shouldKeepTypicalCaseToolAsANetworkFreePlaceholder() {
        McpSchema.CallToolResult result = service.pushTypicalCases(context(Set.of()),
                new McpSchema.CallToolRequest("lexpro_push_typical_cases", Map.of()));

        assertTrue(result.isError());
        assertTrue(text(result).contains("TYPICAL_CASE_PUSH_NOT_IMPLEMENTED"));
        verifyNoInteractions(legalElementClient, entityClient, summaryClient, auditService);
    }

    @Test
    void shouldBoundConcurrentAiInvocationsPerClient() {
        McpProperties limits = new McpProperties();
        limits.setMaxConcurrentRequests(1);
        limits.setMaxConcurrentPerClient(1);
        McpInvocationLimiter limiter = new McpInvocationLimiter(limits);

        McpInvocationLimiter.Permit first = limiter.tryAcquire("client-a");
        assertNotNull(first);
        assertNull(limiter.tryAcquire("client-a"));
        first.close();
        McpInvocationLimiter.Permit afterRelease = limiter.tryAcquire("client-a");
        assertNotNull(afterRelease);
        afterRelease.close();
    }

    private McpTransportContext context(Set<String> authorities) {
        return McpTransportContext.create(Map.of(
                McpRequestContext.CONTEXT_KEY,
                new McpRequestContext(McpRequestContext.PrincipalType.SERVICE, "test-client", null,
                        authorities, "mcp-test-request")
        ));
    }

    private String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }
}
