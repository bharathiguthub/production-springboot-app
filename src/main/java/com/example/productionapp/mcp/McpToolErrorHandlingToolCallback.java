package com.example.productionapp.mcp;

import com.example.productionapp.config.CorrelationIdFilter;
import com.example.productionapp.exception.CustomerNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Translates exceptions raised by a tool callback into a structured {@link McpToolError}.
 * <p>
 * Spring AI's MCP server adapter catches any exception from {@link ToolCallback#call} and returns its
 * message as the text content of a {@code CallToolResult} with {@code isError=true}. This decorator
 * therefore rethrows with the serialized {@link McpToolError} as the message. Successful calls are
 * passed through untouched.
 * <p>
 * The correlation ID is taken from the MCP transport context carried by the tool's exchange, never from
 * the executing thread's MDC: the MCP SDK runs tools on a Reactor worker thread that does not share the
 * HTTP request thread's MDC. For the duration of the call the ID is placed into the worker thread's MDC so
 * that logs written by the tool and the services it calls carry it too.
 */
public class McpToolErrorHandlingToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(McpToolErrorHandlingToolCallback.class);

    static final String INVALID_ARGUMENTS_MESSAGE = "Tool arguments are missing or invalid";
    static final String INTERNAL_ERROR_MESSAGE = "An unexpected error occurred while executing the tool";

    // Used only if serializing the error record itself fails; contains no request-specific data.
    private static final String FALLBACK_ERROR_JSON = "{\"errorCode\":\"INTERNAL_ERROR\",\"message\":\""
            + INTERNAL_ERROR_MESSAGE + "\",\"retryable\":false,\"correlationId\":null}";

    private final ToolCallback delegate;
    private final ObjectMapper objectMapper;

    public McpToolErrorHandlingToolCallback(ToolCallback delegate, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String correlationId = correlationIdFrom(toolContext);
        String previousCorrelationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        putOrRemoveCorrelationId(correlationId);
        try {
            return delegate.call(toolInput, toolContext);
        } catch (RuntimeException ex) {
            throw new McpToolErrorResponseException(toJson(toMcpToolError(ex, correlationId)));
        } finally {
            // Tool calls run on pooled worker threads, so the call must not leave its ID behind.
            putOrRemoveCorrelationId(previousCorrelationId);
        }
    }

    private static String correlationIdFrom(ToolContext toolContext) {
        return McpToolUtils.getMcpExchange(toolContext)
                .map(McpSyncServerExchange::transportContext)
                .map(transportContext -> transportContext.get(CorrelationIdTransportContextExtractor.CORRELATION_ID_KEY))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse(null);
    }

    private static void putOrRemoveCorrelationId(String correlationId) {
        if (correlationId == null) {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        } else {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        }
    }

    private McpToolError toMcpToolError(RuntimeException ex, String correlationId) {
        String toolName = delegate.getToolDefinition().name();

        // MethodToolCallback wraps every exception thrown by the tool method itself in ToolExecutionException.
        if (ex instanceof ToolExecutionException toolExecutionException) {
            return switch (toolExecutionException.getCause()) {
                case McpToolException toolException ->
                        expected(toolName, toolException.getErrorCode(), toolException.getMessage(), correlationId);
                case CustomerNotFoundException notFound ->
                        expected(toolName, McpToolErrorCode.CUSTOMER_NOT_FOUND, notFound.getMessage(), correlationId);
                case null, default -> unexpected(toolName, ex, correlationId);
            };
        }

        // Anything else was raised before the tool method ran, while parsing and binding the JSON arguments.
        // ArithmeticException comes from BigDecimal#longValueExact/#intValueExact for fractional or
        // out-of-range numbers.
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException
                || ex instanceof ArithmeticException) {
            log.warn("MCP tool call rejected toolName={} errorCode={} correlationId={}",
                    toolName, McpToolErrorCode.INVALID_ARGUMENTS, correlationId);
            log.debug("MCP tool argument binding failed toolName={}", toolName, ex);
            return McpToolError.of(McpToolErrorCode.INVALID_ARGUMENTS, INVALID_ARGUMENTS_MESSAGE, correlationId);
        }

        return unexpected(toolName, ex, correlationId);
    }

    private McpToolError expected(String toolName, McpToolErrorCode errorCode, String message, String correlationId) {
        log.warn("MCP tool call rejected toolName={} errorCode={} correlationId={}", toolName, errorCode, correlationId);
        return McpToolError.of(errorCode, message, correlationId);
    }

    private McpToolError unexpected(String toolName, RuntimeException ex, String correlationId) {
        log.error("MCP tool call failed toolName={} errorCode={} correlationId={}",
                toolName, McpToolErrorCode.INTERNAL_ERROR, correlationId, ex);
        return McpToolError.of(McpToolErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE, correlationId);
    }

    private String toJson(McpToolError error) {
        try {
            return objectMapper.writeValueAsString(error);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize MCP tool error errorCode={}", error.errorCode(), ex);
            return FALLBACK_ERROR_JSON;
        }
    }

    /**
     * Carries the serialized error to Spring AI's MCP adapter. It has no cause and no stack trace, so
     * nothing beyond the JSON message can surface from it.
     */
    static final class McpToolErrorResponseException extends RuntimeException {

        McpToolErrorResponseException(String errorJson) {
            super(errorJson, null, false, false);
        }
    }
}
