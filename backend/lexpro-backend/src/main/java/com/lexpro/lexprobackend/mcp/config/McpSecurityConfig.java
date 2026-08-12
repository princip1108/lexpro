package com.lexpro.lexprobackend.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.auth.config.SecurityProblemWriter;
import com.lexpro.lexprobackend.mcp.security.McpClientRegistry;
import com.lexpro.lexprobackend.mcp.security.McpRequestRateLimiter;
import com.lexpro.lexprobackend.mcp.security.McpServiceAuthenticationToken;
import com.lexpro.lexprobackend.mcp.security.McpServiceTokenAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@ConditionalOnProperty(name = "lexpro.mcp.enabled", havingValue = "true")
public class McpSecurityConfig {

    @Bean
    McpClientRegistry mcpClientRegistry(McpProperties properties, ObjectMapper objectMapper) {
        return new McpClientRegistry(properties, objectMapper);
    }

    @Bean
    McpRequestRateLimiter mcpRequestRateLimiter(McpProperties properties) {
        return new McpRequestRateLimiter(properties);
    }

    @Bean
    @Order(1)
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, McpProperties properties,
                                               McpClientRegistry registry,
                                               McpRequestRateLimiter rateLimiter,
                                               SecurityProblemWriter problemWriter) throws Exception {
        McpServiceTokenAuthenticationFilter authenticationFilter =
                new McpServiceTokenAuthenticationFilter(registry, rateLimiter, problemWriter);
        http
                .securityMatcher(properties.getEndpoint())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().access((authentication, context) ->
                        new AuthorizationDecision(authentication.get() instanceof McpServiceAuthenticationToken)))
                .addFilterBefore(authenticationFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> problemWriter.write(
                                request, response, HttpStatus.UNAUTHORIZED, "Authentication required",
                                "MCP_AUTHENTICATION_REQUIRED", "A valid MCP service token is required"))
                        .accessDeniedHandler((request, response, exception) -> problemWriter.write(
                                request, response, HttpStatus.FORBIDDEN, "Access denied",
                                "MCP_ACCESS_DENIED", "The MCP client cannot access this endpoint")));
        return http.build();
    }
}
