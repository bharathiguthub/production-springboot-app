package com.example.productionapp.mcp;

import com.example.productionapp.dto.RedemptionResponse;
import com.example.productionapp.entity.RedemptionStatus;
import com.example.productionapp.service.RedemptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedemptionMcpToolsToolCallbackTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-20T10:15:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-09-20T10:15:30.123456Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RedemptionService redemptionService;

    private ToolCallback redemptionDetailsCallback;

    @BeforeEach
    void setUp() {
        ToolCallback[] callbacks = new McpToolConfig()
                .redemptionToolCallbackProvider(new RedemptionMcpTools(redemptionService), new ObjectMapper())
                .getToolCallbacks();

        assertThat(callbacks).hasSize(1);
        redemptionDetailsCallback = callbacks[0];
    }

    @Test
    void toolDefinition_isRegisteredWithExpectedNameDescriptionAndInputSchema() throws Exception {
        assertThat(redemptionDetailsCallback.getToolDefinition().name()).isEqualTo("get_redemption_details");
        assertThat(redemptionDetailsCallback.getToolDefinition().description())
                .isEqualTo("Retrieve redemption details by redemption ID.");

        JsonNode schema = objectMapper.readTree(redemptionDetailsCallback.getToolDefinition().inputSchema());
        JsonNode redemptionId = schema.get("properties").get("redemptionId");
        assertThat(redemptionId.get("type").asText()).isEqualTo("string");
        assertThat(redemptionId.get("description").asText())
                .isEqualTo("The unique business redemption ID, for example RDM-1001, at most 64 characters");
    }

    @Test
    void call_returnsFailedRedemptionAsJson() throws Exception {
        when(redemptionService.getRedemptionByRedemptionId("RDM-1001")).thenReturn(new RedemptionResponse(
                1L, "RDM-1001", 1L, "PAYPAL", 5000L, new BigDecimal("50.00"), "USD",
                RedemptionStatus.FAILED, "VENDOR_TIMEOUT", CREATED_AT, UPDATED_AT));

        String result = redemptionDetailsCallback.call("{\"redemptionId\": \"RDM-1001\"}");

        JsonNode json = objectMapper.readTree(result);
        assertThat(json.get("id").asLong()).isEqualTo(1L);
        assertThat(json.get("redemptionId").asText()).isEqualTo("RDM-1001");
        assertThat(json.get("customerId").asLong()).isEqualTo(1L);
        assertThat(json.get("vendor").asText()).isEqualTo("PAYPAL");
        assertThat(json.get("points").asLong()).isEqualTo(5000L);
        assertThat(json.get("currency").asText()).isEqualTo("USD");
        assertThat(json.get("status").asText()).isEqualTo("FAILED");
        assertThat(json.get("errorCode").asText()).isEqualTo("VENDOR_TIMEOUT");
        // The amount must stay an exact decimal with its scale, not a rounded double.
        assertThat(result).contains("\"amount\":50.00");
    }

    @Test
    void call_writesInstantsAsIso8601Strings() throws Exception {
        when(redemptionService.getRedemptionByRedemptionId("RDM-1001")).thenReturn(new RedemptionResponse(
                1L, "RDM-1001", 1L, "PAYPAL", 5000L, new BigDecimal("50.00"), "USD",
                RedemptionStatus.FAILED, "VENDOR_TIMEOUT", CREATED_AT, UPDATED_AT));

        JsonNode json = objectMapper.readTree(redemptionDetailsCallback.call("{\"redemptionId\": \"RDM-1001\"}"));

        assertThat(json.get("createdAt").isTextual()).isTrue();
        assertThat(json.get("createdAt").asText()).isEqualTo("2026-09-20T10:15:00Z");
        assertThat(json.get("updatedAt").asText()).isEqualTo("2026-09-20T10:15:30.123456Z");
    }

    @Test
    void call_includesNullErrorCodeForSuccessfulRedemption() throws Exception {
        when(redemptionService.getRedemptionByRedemptionId("RDM-1002")).thenReturn(new RedemptionResponse(
                2L, "RDM-1002", 1L, "PAYPAL", 5000L, new BigDecimal("50.00"), "USD",
                RedemptionStatus.SUCCESS, null, CREATED_AT, UPDATED_AT));

        JsonNode json = objectMapper.readTree(redemptionDetailsCallback.call("{\"redemptionId\": \"RDM-1002\"}"));

        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(json.has("errorCode")).isTrue();
        assertThat(json.get("errorCode").isNull()).isTrue();
    }
}
