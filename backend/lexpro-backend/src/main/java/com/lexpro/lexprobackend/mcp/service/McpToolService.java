package com.lexpro.lexprobackend.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.mcp.McpRequestContext;
import com.lexpro.lexprobackend.mcp.config.McpProperties;
import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.ai.CaseSummaryClient;
import com.lexpro.lexprobackend.processing.ai.CaseSummaryOutput;
import com.lexpro.lexprobackend.processing.ai.EntityRecognitionClient;
import com.lexpro.lexprobackend.processing.ai.EntityRecognitionOutput;
import com.lexpro.lexprobackend.processing.ai.LegalElementRecognitionClient;
import com.lexpro.lexprobackend.processing.ai.LegalElementRecognitionOutput;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.CaseSummarySource;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
@ConditionalOnProperty(name = "lexpro.mcp.enabled", havingValue = "true")
public class McpToolService {

    private static final Logger log = LoggerFactory.getLogger(McpToolService.class);
    private static final Set<String> SUMMARY_TYPES = Set.of("FACT", "PROCESS", "CONCLUSION", "FULL");
    private static final int MAX_SUMMARY_DOCUMENTS = 20;

    private final LegalElementRecognitionClient legalElementClient;
    private final EntityRecognitionClient entityClient;
    private final CaseSummaryClient summaryClient;
    private final AiProcessingProperties aiProperties;
    private final McpInvocationLimiter invocationLimiter;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final McpProperties mcpProperties;

    public McpToolService(LegalElementRecognitionClient legalElementClient,
                          EntityRecognitionClient entityClient,
                          CaseSummaryClient summaryClient,
                          AiProcessingProperties aiProperties,
                          McpInvocationLimiter invocationLimiter,
                          AuditService auditService,
                          ObjectMapper objectMapper,
                          McpProperties mcpProperties) {
        this.legalElementClient = legalElementClient;
        this.entityClient = entityClient;
        this.summaryClient = summaryClient;
        this.aiProperties = aiProperties;
        this.invocationLimiter = invocationLimiter;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.mcpProperties = mcpProperties;
    }

    public McpSchema.CallToolResult recognizeLegalElements(
            McpTransportContext context, McpSchema.CallToolRequest request) {
        return invoke("lexpro_recognize_legal_elements", context, caller -> {
            requireAuthority(caller, "AI_EXECUTE");
            Map<String, Object> args = arguments(request, Set.of("text", "caseCause"));
            String text = requiredText(args, "text", 5, aiProperties.getMaxInputChars());
            String caseCause = optionalText(args, "caseCause", 255);
            ensureAiAvailable();
            LegalElementRecognitionOutput output = withAiCapacity(caller,
                    () -> legalElementClient.recognize(text, caseCause, caller.requestId()));
            JsonNode elements = output.rawElements() == null ? null : output.rawElements().get("elements");
            if (elements == null || !elements.isArray()) {
                throw new ToolException("AI_RESPONSE_INVALID", "The AI legal-element response is invalid");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("caseCause", output.caseCause());
            data.put("elements", elements);
            data.put("validation", output.validationReport());
            return successBody("lexpro_recognize_legal_elements", output.schemaVersion(), output.responseModel(), data);
        });
    }

    public McpSchema.CallToolResult recognizeEntities(
            McpTransportContext context, McpSchema.CallToolRequest request) {
        return invoke("lexpro_recognize_entities", context, caller -> {
            requireAuthority(caller, "AI_EXECUTE");
            Map<String, Object> args = arguments(request, Set.of("text"));
            String text = requiredText(args, "text", 1, aiProperties.getMaxInputChars());
            ensureAiAvailable();
            EntityRecognitionOutput output = withAiCapacity(caller,
                    () -> entityClient.recognize(text, caller.requestId()));
            JsonNode entities = output.entities() == null ? null : output.entities().get("entities");
            if (entities == null || !entities.isArray()) {
                throw new ToolException("AI_RESPONSE_INVALID", "The AI entity response is invalid");
            }
            return successBody("lexpro_recognize_entities", output.schemaVersion(), output.responseModel(),
                    Map.of("entities", entities));
        });
    }

    public McpSchema.CallToolResult summarizeCase(
            McpTransportContext context, McpSchema.CallToolRequest request) {
        return invoke("lexpro_summarize_case", context, caller -> {
            requireAuthority(caller, "AI_EXECUTE");
            Map<String, Object> args = arguments(request, Set.of("summaryType", "documents"));
            String summaryType = requiredText(args, "summaryType", 1, 20);
            if (!SUMMARY_TYPES.contains(summaryType)) {
                throw inputError("summaryType is not supported");
            }
            SummaryInput input = summaryInput(args.get("documents"), summaryType);
            ensureAiAvailable();
            CaseSummaryOutput output = withAiCapacity(caller,
                    () -> summaryClient.summarize(summaryType, input.sources(), caller.requestId()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("summaryType", summaryType);
            data.put("summaryText", output.summaryText());
            data.put("documentIds", input.documentIds());
            return successBody("lexpro_summarize_case", output.schemaVersion(), output.responseModel(), data);
        });
    }

    public McpSchema.CallToolResult pushTypicalCases(
            McpTransportContext context, McpSchema.CallToolRequest request) {
        McpRequestContext caller = McpRequestContext.from(context);
        try {
            arguments(request, Set.of());
        } catch (ToolException exception) {
            return error(exception.errorCode, exception.getMessage());
        }
        log.info("MCP placeholder called tool=lexpro_push_typical_cases clientId={} requestId={}",
                caller.clientId(), caller.requestId());
        return error("TYPICAL_CASE_PUSH_NOT_IMPLEMENTED",
                "Typical-case push is reserved and not implemented");
    }

    private McpSchema.CallToolResult invoke(String toolName, McpTransportContext transportContext,
                                             Function<McpRequestContext, Object> action) {
        McpRequestContext caller = McpRequestContext.from(transportContext);
        McpSchema.CallToolResult response;
        AuditResult auditResult;
        String errorCode = null;
        try {
            response = success(action.apply(caller));
            auditResult = AuditResult.SUCCESS;
        } catch (ToolException exception) {
            errorCode = exception.errorCode;
            response = error(errorCode, exception.getMessage());
            auditResult = AuditResult.FAILED;
        } catch (AiClientException exception) {
            errorCode = safeAiErrorCode(exception.getErrorCode());
            log.warn("MCP AI response rejected tool={} clientId={} requestId={} errorCode={} reason={}",
                    toolName, caller.clientId(), caller.requestId(), errorCode, exception.getMessage());
            response = error(errorCode, safeAiMessage(errorCode));
            auditResult = AuditResult.FAILED;
        } catch (RuntimeException exception) {
            errorCode = "MCP_TOOL_FAILED";
            log.error("MCP tool failed tool={} clientId={} requestId={}",
                    toolName, caller.clientId(), caller.requestId(), exception);
            response = error(errorCode, "The tool could not complete the request");
            auditResult = AuditResult.FAILED;
        }
        try {
            audit(caller, toolName, auditResult, errorCode);
        } catch (RuntimeException exception) {
            log.error("MCP audit failed tool={} clientId={} requestId={}",
                    toolName, caller.clientId(), caller.requestId(), exception);
            return error("MCP_AUDIT_UNAVAILABLE", "The tool audit record could not be written");
        }
        return response;
    }

    private Object successBody(String tool, String schemaVersion, String model, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tool", tool);
        body.put("schemaVersion", schemaVersion);
        body.put("model", model);
        body.put("data", data);
        return body;
    }

    private McpSchema.CallToolResult success(Object value) {
        Object structured = objectMapper.convertValue(value, Object.class);
        String json = json(structured);
        if (json.length() > mcpProperties.getMaxOutputChars()) {
            throw new ToolException("MCP_OUTPUT_LIMIT_EXCEEDED", "The result exceeds the configured output limit");
        }
        return McpSchema.CallToolResult.builder()
                .structuredContent(structured)
                .addTextContent(json)
                .isError(false)
                .build();
    }

    private McpSchema.CallToolResult error(String errorCode, String message) {
        Map<String, Object> body = Map.of("errorCode", errorCode, "message", message);
        return McpSchema.CallToolResult.builder()
                .structuredContent(body)
                .addTextContent(json(body))
                .isError(true)
                .build();
    }

    private void ensureAiAvailable() {
        if (!aiProperties.isEnabled()) {
            throw new ToolException("AI_PROVIDER_DISABLED", "AI processing is disabled");
        }
        if (!aiProperties.isAllowExternalCaseData()) {
            throw new ToolException("AI_DATA_EXPORT_DISABLED", "External AI data transfer is not enabled");
        }
    }

    private <T> T withAiCapacity(McpRequestContext caller, java.util.function.Supplier<T> action) {
        McpInvocationLimiter.Permit permit = invocationLimiter.tryAcquire(caller.clientId());
        if (permit == null) {
            throw new ToolException("MCP_CAPACITY_EXCEEDED", "The MCP AI capacity limit was exceeded");
        }
        try (permit) {
            return action.get();
        }
    }

    private void requireAuthority(McpRequestContext caller, String authority) {
        if (!caller.authorities().contains(authority)) {
            throw new ToolException("MCP_ACCESS_DENIED", "The caller is not allowed to use this tool");
        }
    }

    private Map<String, Object> arguments(McpSchema.CallToolRequest request, Set<String> allowed) {
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        for (String name : arguments.keySet()) {
            if (!allowed.contains(name)) {
                throw inputError("Unknown argument: " + name);
            }
        }
        return arguments;
    }

    private String requiredText(Map<String, Object> args, String name, int minLength, int maxLength) {
        Object value = args.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw inputError(name + " must be a nonblank string");
        }
        if (text.length() < minLength || text.length() > maxLength) {
            throw inputError(name + " is outside the allowed length");
        }
        return text;
    }

    private String optionalText(Map<String, Object> args, String name, int maxLength) {
        Object value = args.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank() || text.length() > maxLength) {
            throw inputError(name + " must be a nonblank string within the allowed length");
        }
        return text.trim();
    }

    private SummaryInput summaryInput(Object value, String summaryType) {
        int maxDocuments = Math.min(MAX_SUMMARY_DOCUMENTS, mcpProperties.getMaxItems());
        if (!(value instanceof List<?> documents) || documents.isEmpty() || documents.size() > maxDocuments) {
            throw inputError("documents must contain between 1 and " + maxDocuments + " items");
        }
        List<CaseSummarySource> sources = new ArrayList<>();
        List<String> documentIds = new ArrayList<>();
        long inputLength = "Summary type: ".length() + summaryType.length() + 1L;
        for (int index = 0; index < documents.size(); index++) {
            Object item = documents.get(index);
            if (!(item instanceof Map<?, ?> rawDocument)) {
                throw inputError("Each document must be an object");
            }
            Map<String, Object> document = stringKeyMap(rawDocument);
            rejectUnknown(document, Set.of("documentId", "text"));
            String text = requiredText(document, "text", 1, aiProperties.getMaxInputChars());
            String documentId = optionalText(document, "documentId", 100);
            long sourceId = index + 1L;
            inputLength += ("\n[DOCUMENT " + sourceId + "]\n").length() + text.length() + 1L;
            if (inputLength > aiProperties.getMaxInputChars()) {
                throw new ToolException("AI_INPUT_TOO_LARGE", "The combined summary input is too large");
            }
            sources.add(new CaseSummarySource(sourceId, "SUCCEEDED", text));
            if (documentId != null) {
                documentIds.add(documentId);
            }
        }
        return new SummaryInput(List.copyOf(sources), List.copyOf(documentIds));
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw inputError("Document field names must be strings");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private void rejectUnknown(Map<String, Object> values, Set<String> allowed) {
        for (String name : values.keySet()) {
            if (!allowed.contains(name)) {
                throw inputError("Unknown document field: " + name);
            }
        }
    }

    private void audit(McpRequestContext caller, String toolName, AuditResult result, String errorCode) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("transport", "STREAMABLE_HTTP_STATELESS");
        detail.put("principalType", caller.principalType().name());
        detail.put("clientId", caller.clientId());
        detail.put("tool", toolName);
        if (errorCode != null) {
            detail.put("errorCode", errorCode);
        }
        auditService.recordIndependent(new AuditEvent(
                caller.userId(), null, "MCP_TOOL_CALLED", "MCP_TOOL", toolName, result, detail),
                caller.requestId());
    }

    private String safeAiErrorCode(String errorCode) {
        return Set.of("AI_PROVIDER_DISABLED", "AI_PROVIDER_REJECTED", "AI_PROVIDER_UNAVAILABLE",
                "AI_RESPONSE_INVALID").contains(errorCode) ? errorCode : "MCP_TOOL_FAILED";
    }

    private String safeAiMessage(String errorCode) {
        return switch (errorCode) {
            case "AI_PROVIDER_DISABLED" -> "AI processing is disabled";
            case "AI_PROVIDER_REJECTED" -> "The AI provider rejected the request";
            case "AI_PROVIDER_UNAVAILABLE" -> "The AI provider is unavailable";
            case "AI_RESPONSE_INVALID" -> "The AI provider returned an invalid structured response";
            default -> "The tool could not complete the request";
        };
    }

    private ToolException inputError(String message) {
        return new ToolException("MCP_INPUT_INVALID", message);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ToolException("MCP_OUTPUT_INVALID", "The result could not be serialized");
        }
    }

    private record SummaryInput(List<CaseSummarySource> sources, List<String> documentIds) {}

    private static final class ToolException extends RuntimeException {
        private final String errorCode;

        private ToolException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
    }
}
