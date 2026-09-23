package com.example.productionapp.mcp;

/**
 * Expected, client-facing MCP tool failure. The message is returned to the MCP client as-is, so it must
 * not contain internal details.
 */
public class McpToolException extends RuntimeException {

    private final McpToolErrorCode errorCode;

    public McpToolException(McpToolErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public McpToolErrorCode getErrorCode() {
        return errorCode;
    }
}
