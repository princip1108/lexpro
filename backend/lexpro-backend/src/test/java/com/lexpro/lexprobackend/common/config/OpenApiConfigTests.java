package com.lexpro.lexprobackend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiConfigTests {

    @Test
    void shouldDescribeVersionedLexproApi() {
        OpenAPI openAPI = new OpenApiConfig().lexproOpenApi();

        assertEquals("LexPro API", openAPI.getInfo().getTitle());
        assertEquals("v1", openAPI.getInfo().getVersion());
        assertEquals(
                "bearer",
                openAPI.getComponents().getSecuritySchemes().get("bearerAuth").getScheme()
        );
    }
}
