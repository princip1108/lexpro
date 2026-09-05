package com.lexpro.lexprobackend.mcp.service;

import com.lexpro.lexprobackend.mcp.config.McpProperties;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.recommendation.config.PartnerTypicalCaseProperties;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Component
@ConditionalOnProperty(name = "lexpro.mcp.enabled", havingValue = "true")
public class McpToolCatalog {

    private static final int MAX_SUMMARY_DOCUMENTS = 20;

    private final McpToolService toolService;
    private final McpProperties mcpProperties;
    private final AiProcessingProperties aiProperties;
    private final PartnerTypicalCaseProperties partnerProperties;

    public McpToolCatalog(McpToolService toolService, McpProperties mcpProperties,
                          AiProcessingProperties aiProperties,
                          PartnerTypicalCaseProperties partnerProperties) {
        this.toolService = toolService;
        this.mcpProperties = mcpProperties;
        this.aiProperties = aiProperties;
        this.partnerProperties = partnerProperties;
    }

    public List<McpStatelessServerFeatures.SyncToolSpecification> tools() {
        return List.of(
                specification(tool("lexpro_recognize_legal_elements", "Recognize legal elements",
                                "Extract traceable legal elements from supplied legal text.", legalElementSchema()),
                        toolService::recognizeLegalElements),
                specification(tool("lexpro_recognize_entities", "Recognize document entities",
                                "Extract validated entities and source offsets from supplied legal text.",
                                entitySchema()), toolService::recognizeEntities),
                specification(tool("lexpro_summarize_case", "Summarize case documents",
                                "Generate a structured case summary from one or more supplied documents.",
                                summarySchema()), toolService::summarizeCase),
                specification(tool("lexpro_push_typical_cases", "Recommend typical cases",
                                "Analyze supplied case facts and return ranked typical-case recommendations.",
                                typicalCaseSchema()), toolService::pushTypicalCases)
        );
    }

    private McpStatelessServerFeatures.SyncToolSpecification specification(
            McpSchema.Tool tool,
            BiFunction<McpTransportContext, McpSchema.CallToolRequest, McpSchema.CallToolResult> call) {
        return McpStatelessServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(call)
                .build();
    }

    private McpSchema.Tool tool(String name, String title, String description, McpSchema.JsonSchema inputSchema) {
        return McpSchema.Tool.builder()
                .name(name)
                .title(title)
                .description(description)
                .inputSchema(inputSchema)
                .annotations(new McpSchema.ToolAnnotations(title, true, false, false, false, false))
                .build();
    }

    private McpSchema.JsonSchema legalElementSchema() {
        return schema(Map.of(
                "text", string(5, aiProperties.getMaxInputChars()),
                "caseCause", string(1, 255)
        ), List.of("text"));
    }

    private McpSchema.JsonSchema entitySchema() {
        return schema(Map.of("text", string(1, aiProperties.getMaxInputChars())), List.of("text"));
    }

    private McpSchema.JsonSchema summarySchema() {
        Map<String, Object> documentFields = new LinkedHashMap<>();
        documentFields.put("documentId", string(1, 100));
        documentFields.put("text", string(1, aiProperties.getMaxInputChars()));
        Map<String, Object> documentSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("text"),
                "properties", documentFields
        );
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("summaryType", Map.of("type", "string", "enum",
                List.of("FACT", "PROCESS", "CONCLUSION", "FULL")));
        fields.put("documents", Map.of(
                "type", "array",
                "minItems", 1,
                "maxItems", maxSummaryDocuments(),
                "items", documentSchema
        ));
        return schema(fields, List.of("summaryType", "documents"));
    }

    private McpSchema.JsonSchema typicalCaseSchema() {
        Map<String, Object> filterFields = new LinkedHashMap<>();
        filterFields.put("title", string(1, 255));
        filterFields.put("caseCauses", stringArray(50, 255));
        filterFields.put("applicableLaws", stringArray(100, 500));
        filterFields.put("caseLevel", string(1, 50));
        filterFields.put("courtLevel", string(1, 50));
        filterFields.put("region", string(1, 100));
        filterFields.put("judgmentDateThrough", Map.of("type", "string", "format", "date"));
        filterFields.put("procedure", string(1, 100));
        filterFields.put("docType", string(1, 50));
        filterFields.put("court", string(1, 255));
        filterFields.put("caseType", string(1, 50));

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("factText", string(1, 100_000));
        fields.put("limit", Map.of(
                "type", "integer",
                "minimum", 1,
                "maximum", Math.min(50, partnerProperties.getMaxResults())));
        fields.put("filters", Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", filterFields));
        return schema(fields, List.of("factText"));
    }

    private McpSchema.JsonSchema schema(Map<String, Object> fields, List<String> required) {
        return new McpSchema.JsonSchema("object", fields, required, false, null, null);
    }

    private Map<String, Object> string(int minimum, int maximum) {
        return Map.of("type", "string", "minLength", minimum, "maxLength", maximum);
    }

    private Map<String, Object> stringArray(int maximumItems, int maximumItemLength) {
        return Map.of(
                "type", "array",
                "maxItems", maximumItems,
                "items", string(1, maximumItemLength));
    }

    private int maxSummaryDocuments() {
        return Math.min(MAX_SUMMARY_DOCUMENTS, mcpProperties.getMaxItems());
    }
}
