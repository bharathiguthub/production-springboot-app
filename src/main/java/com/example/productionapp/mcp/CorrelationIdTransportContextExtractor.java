package com.example.productionapp.mcp;

import com.example.productionapp.config.CorrelationIdFilter;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.Map;

/**
 * Copies the correlation ID resolved by {@link CorrelationIdFilter} into the MCP transport context of each
 * incoming MCP message.
 * <p>
 * The MCP SDK runs tool calls on a Reactor worker thread, where the HTTP request thread's MDC is not
 * available. The transport context, by contrast, is created per request and handed to the tool through its
 * {@code McpSyncServerExchange}, so it is the value tools must read the correlation ID from.
 */
public class CorrelationIdTransportContextExtractor implements McpTransportContextExtractor<ServerRequest> {

    public static final String CORRELATION_ID_KEY = "correlationId";

    @Override
    public McpTransportContext extract(ServerRequest request) {
        return request.attribute(CorrelationIdFilter.REQUEST_ATTRIBUTE)
                .filter(String.class::isInstance)
                .map(correlationId -> McpTransportContext.create(Map.of(CORRELATION_ID_KEY, correlationId)))
                .orElse(McpTransportContext.EMPTY);
    }
}
