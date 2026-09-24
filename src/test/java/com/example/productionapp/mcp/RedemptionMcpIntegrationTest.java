package com.example.productionapp.mcp;

import com.example.productionapp.AbstractIntegrationTest;
import com.example.productionapp.config.CorrelationIdFilter;
import com.example.productionapp.entity.Customer;
import com.example.productionapp.entity.Redemption;
import com.example.productionapp.entity.RedemptionStatus;
import com.example.productionapp.repository.CustomerRepository;
import com.example.productionapp.repository.RedemptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives get_redemption_details through the real MCP server over HTTP/SSE, from tool registration down to
 * the PostgreSQL container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedemptionMcpIntegrationTest extends AbstractIntegrationTest {

    private static final String CORRELATION_ID = "redemption-it-correlation-id";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private RedemptionRepository redemptionRepository;

    private Customer storedCustomer;

    private Redemption storedRedemption;

    private McpSyncClient client;

    @BeforeEach
    void setUp() {
        storeFailedRedemption();
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder("http://localhost:" + port)
                .requestBuilder(HttpRequest.newBuilder().header(CorrelationIdFilter.CORRELATION_ID_HEADER, CORRELATION_ID))
                .build();
        client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
        client.initialize();
    }

    // The rows are committed and the container is shared by all integration tests, so they must be removed;
    // redemptions first, since fk_redemptions_customer rejects deleting a customer that still has them.
    @AfterEach
    void tearDown() {
        client.closeGracefully();
        redemptionRepository.deleteById(storedRedemption.getId());
        customerRepository.deleteById(storedCustomer.getId());
    }

    @Test
    void listTools_includesRedemptionToolAlongsideCustomerTools() {
        assertThat(client.listTools().tools()).extracting(Tool::name)
                .contains("get_customer_details", "get_customer_list", "get_redemption_details");
    }

    @Test
    void getRedemptionDetails_returnsStoredRedemption() throws Exception {
        CallToolResult result = client.callTool(new CallToolRequest(
                "get_redemption_details", Map.of("redemptionId", storedRedemption.getRedemptionId())));

        assertThat(result.isError()).isFalse();
        JsonNode redemption = objectMapper.readTree(resultText(result));
        assertThat(redemption.get("id").asLong()).isEqualTo(storedRedemption.getId());
        assertThat(redemption.get("redemptionId").asText()).isEqualTo(storedRedemption.getRedemptionId());
        assertThat(redemption.get("customerId").asLong()).isEqualTo(storedCustomer.getId());
        assertThat(redemption.get("vendor").asText()).isEqualTo("PAYPAL");
        assertThat(redemption.get("points").asLong()).isEqualTo(5000L);
        assertThat(redemption.get("amount").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(redemption.get("currency").asText()).isEqualTo("USD");
        assertThat(redemption.get("status").asText()).isEqualTo("FAILED");
        assertThat(redemption.get("errorCode").asText()).isEqualTo("VENDOR_TIMEOUT");
        assertIso8601Instant(redemption.get("createdAt"), storedRedemption.getCreatedAt());
        assertIso8601Instant(redemption.get("updatedAt"), storedRedemption.getUpdatedAt());
    }

    @Test
    void getRedemptionDetails_returnsStructuredNotFoundErrorWithCorrelationId() throws Exception {
        CallToolResult result = client.callTool(
                new CallToolRequest("get_redemption_details", Map.of("redemptionId", "RDM-DOES-NOT-EXIST")));

        assertThat(result.isError()).isTrue();
        JsonNode error = objectMapper.readTree(resultText(result));
        assertThat(error.get("errorCode").asText()).isEqualTo("REDEMPTION_NOT_FOUND");
        assertThat(error.get("message").asText()).isEqualTo("Redemption not found with redemptionId: RDM-DOES-NOT-EXIST");
        assertThat(error.get("retryable").asBoolean()).isFalse();
        assertThat(error.get("correlationId").asText()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void getRedemptionDetails_rejectsBlankRedemptionIdWithCorrelationId() throws Exception {
        CallToolResult result = client.callTool(
                new CallToolRequest("get_redemption_details", Map.of("redemptionId", " ")));

        assertThat(result.isError()).isTrue();
        JsonNode error = objectMapper.readTree(resultText(result));
        assertThat(error.get("errorCode").asText()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(error.get("message").asText()).isEqualTo("redemptionId must not be blank");
        assertThat(error.get("correlationId").asText()).isEqualTo(CORRELATION_ID);
    }

    /**
     * Stores rows with unique identifiers, since the PostgreSQL container is shared by all integration tests,
     * and keeps the redemption as re-read from the database so its timestamps carry the stored precision.
     */
    private void storeFailedRedemption() {
        String suffix = UUID.randomUUID().toString();
        storedCustomer = customerRepository.saveAndFlush(
                new Customer("RDM-IT-" + suffix, "Red", "Emption", "rdm-it-" + suffix + "@example.com"));
        Redemption saved = redemptionRepository.saveAndFlush(new Redemption(
                "RDM-IT-" + suffix, storedCustomer.getId(), "PAYPAL", 5000L, new BigDecimal("50.00"), "USD",
                RedemptionStatus.FAILED, "VENDOR_TIMEOUT"));
        storedRedemption = redemptionRepository.findById(saved.getId()).orElseThrow();
    }

    private static void assertIso8601Instant(JsonNode value, Instant expected) {
        assertThat(value.isTextual()).as("expected an ISO-8601 string but was %s", value).isTrue();
        assertThat(Instant.parse(value.asText())).isEqualTo(expected);
    }

    private static String resultText(CallToolResult result) {
        assertThat(result.content()).singleElement().isInstanceOf(TextContent.class);
        return ((TextContent) result.content().get(0)).text();
    }
}
