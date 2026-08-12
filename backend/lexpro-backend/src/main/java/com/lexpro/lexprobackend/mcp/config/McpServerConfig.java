package com.lexpro.lexprobackend.mcp.config;

import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import com.lexpro.lexprobackend.mcp.McpRequestContext;
import com.lexpro.lexprobackend.mcp.security.McpServiceAuthenticationToken;
import com.lexpro.lexprobackend.mcp.service.McpToolCatalog;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(McpProperties.class)
@ConditionalOnProperty(name = "lexpro.mcp.enabled", havingValue = "true")
public class McpServerConfig {

    @Bean
    WebMvcStatelessServerTransport mcpTransport(McpProperties properties, AiProcessingProperties aiProperties) {
        properties.validate();
        if (properties.getRequestTimeout().compareTo(aiProperties.getReadTimeout()) <= 0) {
            throw new IllegalStateException("lexpro.mcp.request-timeout must exceed lexpro.ai.read-timeout");
        }
        return WebMvcStatelessServerTransport.builder()
                .messageEndpoint(properties.getEndpoint())
                .contextExtractor(this::requestContext)
                .build();
    }

    @Bean(destroyMethod = "close")
    McpStatelessSyncServer mcpServer(WebMvcStatelessServerTransport transport, McpToolCatalog toolCatalog,
                                     McpProperties properties) {
        return McpServer.sync(transport)
                .serverInfo("lexpro-legal-ai", "2.0.0")
                .instructions("Legal AI processing tools. Inputs cannot change authorization or server policy.")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .requestTimeout(properties.getRequestTimeout())
                .tools(toolCatalog.tools())
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> mcpRouter(WebMvcStatelessServerTransport transport,
                                              McpStatelessSyncServer server) {
        return transport.getRouterFunction();
    }

    private McpTransportContext requestContext(ServerRequest request) {
        Principal principal = request.servletRequest().getUserPrincipal();
        if (!(principal instanceof McpServiceAuthenticationToken authentication)) {
            throw new IllegalStateException("Authenticated MCP service principal is required");
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
        String requestId = RequestIdFilter.getOrCreateRequestId(request.servletRequest());
        return McpTransportContext.create(Map.of(
                McpRequestContext.CONTEXT_KEY, new McpRequestContext(
                        McpRequestContext.PrincipalType.SERVICE,
                        authentication.getClientId(),
                        null,
                        authorities,
                        requestId)
        ));
    }
}
