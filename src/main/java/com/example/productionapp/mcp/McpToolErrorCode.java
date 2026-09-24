package com.example.productionapp.mcp;

public enum McpToolErrorCode {

    INVALID_PAGE(false),
    INVALID_PAGE_SIZE(false),
    INVALID_ARGUMENTS(false),
    CUSTOMER_NOT_FOUND(false),
    REDEMPTION_NOT_FOUND(false),
    INTERNAL_ERROR(false);

    private final boolean retryable;

    McpToolErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
