package com.lexpro.lexprobackend.processing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.processing.ai.OpenAiCompatibleStructuredAiClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AiProcessingConfigTests {
    @Test
    void shouldSendJsonWithoutH2cUpgradeToLocalModelServer() throws Exception {
        var upgrade = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"model\":\"LexPro_8B\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var properties = new AiProcessingProperties();
            properties.setEnabled(true);
            properties.setApiKey("EMPTY");
            properties.setModel("LexPro_8B");
            properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"));
            var client = new OpenAiCompatibleStructuredAiClient(new AiProcessingConfig().aiRestClient(properties), properties, new ObjectMapper());
            assertTrue(client.generate("Return JSON", "Fictional test", "test").content().path("ok").asBoolean());
            assertNull(upgrade.get());
            assertEquals("LexPro_8B", new ObjectMapper().readTree(body.get()).path("model").asText());
        } finally {
            server.stop(0);
        }
    }
}
