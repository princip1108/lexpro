package com.lexpro.lexprobackend.mcp.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;

@JsonComponent
public class McpErrorJsonSerializer extends JsonSerializer<McpError> {

    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int INTERNAL_ERROR = -32603;

    @Override
    public void serialize(McpError value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        McpSchema.JSONRPCResponse.JSONRPCError sdkError = value.getJsonRpcError();
        int code;
        String message;
        if (sdkError != null) {
            code = sdkError.code();
            message = safeMessage(sdkError.message());
        } else if ("Invalid message format".equals(value.getMessage())) {
            code = PARSE_ERROR;
            message = "Invalid message format";
        } else if ("The server accepts either requests or notifications".equals(value.getMessage())) {
            code = INVALID_REQUEST;
            message = "Invalid request";
        } else {
            code = INTERNAL_ERROR;
            message = "Internal server error";
        }

        generator.writeStartObject();
        generator.writeStringField("jsonrpc", "2.0");
        generator.writeNullField("id");
        generator.writeObjectFieldStart("error");
        generator.writeNumberField("code", code);
        generator.writeStringField("message", message);
        generator.writeEndObject();
        generator.writeEndObject();
    }

    private String safeMessage(String message) {
        return switch (message) {
            case "Invalid message format" -> "Invalid message format";
            case "Invalid request" -> "Invalid request";
            case "Method not found" -> "Method not found";
            case "Invalid params" -> "Invalid params";
            default -> "Internal server error";
        };
    }
}
