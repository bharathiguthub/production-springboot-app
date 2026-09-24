package com.example.productionapp.mcp;

import com.example.productionapp.dto.RedemptionResponse;
import com.example.productionapp.service.RedemptionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class RedemptionMcpTools {

    // Matches the redemptions.redemption_id column length.
    private static final int MAX_REDEMPTION_ID_LENGTH = 64;

    private final RedemptionService redemptionService;

    public RedemptionMcpTools(RedemptionService redemptionService) {
        this.redemptionService = redemptionService;
    }

    @Tool(name = "get_redemption_details", description = "Retrieve redemption details by redemption ID.",
            resultConverter = McpToolResultConverter.class)
    public RedemptionResponse getRedemptionDetails(
            @ToolParam(description = "The unique business redemption ID, for example RDM-1001, at most 64 characters")
            String redemptionId) {
        // Spring AI binds a missing or JSON-null argument as null, so it reaches this method.
        if (redemptionId == null) {
            throw new McpToolException(McpToolErrorCode.INVALID_ARGUMENTS, "redemptionId is required");
        }
        if (redemptionId.isBlank()) {
            throw new McpToolException(McpToolErrorCode.INVALID_ARGUMENTS, "redemptionId must not be blank");
        }
        if (redemptionId.length() > MAX_REDEMPTION_ID_LENGTH) {
            throw new McpToolException(McpToolErrorCode.INVALID_ARGUMENTS, "redemptionId must be at most 64 characters");
        }
        return redemptionService.getRedemptionByRedemptionId(redemptionId);
    }
}
