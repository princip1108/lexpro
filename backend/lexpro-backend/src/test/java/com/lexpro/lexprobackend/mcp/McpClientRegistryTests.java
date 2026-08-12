package com.lexpro.lexprobackend.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.mcp.config.McpProperties;
import com.lexpro.lexprobackend.mcp.security.McpClientRegistry;
import com.lexpro.lexprobackend.mcp.security.McpRequestRateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientRegistryTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldAuthenticateOnlyAnEnabledUnexpiredToken() throws Exception {
        String token = "registry-test-token-with-at-least-thirty-two-characters";
        Path registryFile = writeRegistry(token, true, Instant.now().plus(1, ChronoUnit.HOURS), "");
        McpClientRegistry registry = new McpClientRegistry(properties(registryFile),
                new ObjectMapper().findAndRegisterModules());

        var identity = registry.authenticate(token);

        assertTrue(identity.isPresent());
        assertEquals("registry-test-client", identity.get().clientId());
        assertTrue(identity.get().authorities().contains("AI_EXECUTE"));
        assertTrue(registry.authenticate("different-token-with-at-least-thirty-two-characters").isEmpty());
    }

    @Test
    void shouldRejectExpiredTokensAndUnknownRegistryFields() throws Exception {
        String token = "registry-test-token-with-at-least-thirty-two-characters";
        Path expired = writeRegistry(token, true, Instant.now().minus(1, ChronoUnit.HOURS), "");
        McpClientRegistry registry = new McpClientRegistry(properties(expired),
                new ObjectMapper().findAndRegisterModules());
        assertTrue(registry.authenticate(token).isEmpty());

        Path invalid = writeRegistry(token, true, Instant.now().plus(1, ChronoUnit.HOURS),
                ",\"unexpected\":true");
        assertThrows(IllegalStateException.class, () -> new McpClientRegistry(properties(invalid),
                new ObjectMapper().findAndRegisterModules()));
    }

    @Test
    void shouldApplyTheConfiguredPerClientRateLimit() {
        McpProperties properties = new McpProperties();
        properties.setRateLimitPerMinute(1);
        McpRequestRateLimiter limiter = new McpRequestRateLimiter(properties);

        assertTrue(limiter.allow("client-a"));
        assertFalse(limiter.allow("client-a"));
        assertTrue(limiter.allow("client-b"));
    }

    private McpProperties properties(Path registryFile) {
        McpProperties properties = new McpProperties();
        properties.setClientRegistryPath(registryFile.toAbsolutePath().toString());
        return properties;
    }

    private Path writeRegistry(String token, boolean enabled, Instant expiresAt, String extraField) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        String json = """
                {
                  "schemaVersion":"mcp-client-registry-v1",
                  "clients":[{
                    "clientId":"registry-test-client",
                    "enabled":%s,
                    "authorities":["AI_EXECUTE"],
                    "tokens":[{
                      "tokenId":"test-token",
                      "tokenSha256":"%s",
                      "expiresAt":"%s"
                    }]
                  }]
                  %s
                }
                """.formatted(enabled, hash, expiresAt, extraField);
        Path file = tempDir.resolve("registry-" + System.nanoTime() + ".json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }
}
