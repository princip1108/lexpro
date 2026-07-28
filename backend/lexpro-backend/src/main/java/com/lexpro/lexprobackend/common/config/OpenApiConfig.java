package com.lexpro.lexprobackend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI lexproOpenApi() {
        return new OpenAPI().info(
                new Info()
                        .title("LexPro API")
                        .description("LexPro backend application API")
                        .version("v1")
        );
    }
}
