package com.example.productionapp.mcp;

/**
 * Machine-readable error returned to MCP clients as the text content of a tool result flagged with
 * {@code isError=true}.
 */
public record McpToolError(
        McpToolErrorCode errorCode,
        String message,
        boolean retryable,
        String correlationId
) {

    public static McpToolError of(McpToolErrorCode errorCode, String message, String correlationId) {
        return new McpToolError(errorCode, message, errorCode.isRetryable(), correlationId);
    }
}
