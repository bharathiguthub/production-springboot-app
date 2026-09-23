package com.example.productionapp.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import org.springframework.ai.mcp.server.autoconfigure.McpServerProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Replaces Spring AI's auto-configured WebMVC SSE transport only to register
 * {@link CorrelationIdTransportContextExtractor}; the auto-configuration offers no other hook for it.
 * <p>
 * Everything else mirrors {@code McpWebMvcServerAutoConfiguration} (Spring AI 1.0.9) and must be kept in
 * line with it on upgrade. That auto-configuration backs off as a whole once a transport provider bean
 * exists, so its router function has to be declared here as well.
 */
@Configuration
@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class McpTransportConfig {

    @Bean
    public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider(
            @Qualifier("mcpServerObjectMapper") ObjectMapper objectMapper, McpServerProperties serverProperties) {
        return WebMvcSseServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .baseUrl(serverProperties.getBaseUrl())
                .sseEndpoint(serverProperties.getSseEndpoint())
                .messageEndpoint(serverProperties.getSseMessageEndpoint())
                .contextExtractor(new CorrelationIdTransportContextExtractor())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mvcMcpRouterFunction(WebMvcSseServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }
}
