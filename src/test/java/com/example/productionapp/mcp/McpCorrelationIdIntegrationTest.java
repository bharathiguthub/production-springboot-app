package com.example.productionapp.mcp;

import com.example.productionapp.AbstractIntegrationTest;
import com.example.productionapp.config.CorrelationIdFilter;
import com.example.productionapp.entity.Customer;
import com.example.productionapp.repository.CustomerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real MCP server over HTTP/SSE, so tool calls take the SDK's actual path: the POST is handled
 * on a servlet thread while the tool runs on a Reactor worker thread.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpCorrelationIdIntegrationTest extends AbstractIntegrationTest {

    private static final String CLIENT_CORRELATION_ID_KEY = "clientCorrelationId";
    private static final Map<String, Object> INVALID_PAGE_ARGUMENTS = Map.of("page", -1, "size", 10);

    // Client-side only: lets each calling thread choose the X-Correlation-Id header for its own request.
    private static final ThreadLocal<String> outgoingCorrelationId = new ThreadLocal<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private CustomerRepository customerRepository;

    private final List<Long> storedCustomerIds = new ArrayList<>();

    private McpSyncClient client;

    @BeforeEach
    void connect() {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder("http://localhost:" + port)
                .httpRequestCustomizer((builder, method, endpoint, body, context) -> {
                    if (context.get(CLIENT_CORRELATION_ID_KEY) instanceof String correlationId) {
                        builder.header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
                    }
                })
                .build();
        client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .transportContextProvider(() -> outgoingCorrelationId.get() == null
                        ? McpTransportContext.EMPTY
                        : McpTransportContext.create(Map.of(CLIENT_CORRELATION_ID_KEY, outgoingCorrelationId.get())))
                .build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {
        client.closeGracefully();
    }

    // The tool calls commit through the real server, so the rows would otherwise outlive this test and leak
    // into other integration tests that share the container.
    @AfterEach
    void deleteStoredCustomers() {
        customerRepository.deleteAllById(storedCustomerIds);
    }

    @Test
    void invalidPage_errorCarriesIncomingCorrelationId() throws Exception {
        CallToolResult result = callWithCorrelationId("it-correlation-id", "get_customer_list", INVALID_PAGE_ARGUMENTS);

        assertThat(result.isError()).isTrue();
        JsonNode error = errorJson(result);
        assertThat(error.get("errorCode").asText()).isEqualTo("INVALID_PAGE");
        assertThat(error.get("message").asText()).isEqualTo("page must be greater than or equal to 0");
        assertThat(error.get("retryable").asBoolean()).isFalse();
        assertThat(error.get("correlationId").asText()).isEqualTo("it-correlation-id");
    }

    @Test
    void customerNotFound_errorFromServiceCarriesIncomingCorrelationId() throws Exception {
        CallToolResult result = callWithCorrelationId(
                "it-not-found-id", "get_customer_details", Map.of("customerId", Long.MAX_VALUE));

        JsonNode error = errorJson(result);
        assertThat(result.isError()).isTrue();
        assertThat(error.get("errorCode").asText()).isEqualTo("CUSTOMER_NOT_FOUND");
        assertThat(error.get("correlationId").asText()).isEqualTo("it-not-found-id");
    }

    @Test
    void withoutIncomingHeader_errorCarriesGeneratedCorrelationIdPerRequest() throws Exception {
        String first = errorJson(client.callTool(new CallToolRequest("get_customer_list", INVALID_PAGE_ARGUMENTS)))
                .get("correlationId").asText();
        String second = errorJson(client.callTool(new CallToolRequest("get_customer_list", INVALID_PAGE_ARGUMENTS)))
                .get("correlationId").asText();

        assertThat(first).isNotBlank().isNotEqualTo("null");
        assertThat(second).isNotBlank().isNotEqualTo("null").isNotEqualTo(first);
    }

    @Test
    void successfulCall_isStillServedThroughOverriddenTransport() {
        CallToolResult result = client.callTool(new CallToolRequest("get_customer_list", Map.of("page", 0, "size", 10)));

        assertThat(result.isError()).isFalse();
        assertThat(resultText(result)).contains("\"content\"");
    }

    @Test
    void customerDetails_returnsInstantsAsIso8601StringsThroughRealTransport() throws Exception {
        Customer stored = storeCustomer();

        CallToolResult result = client.callTool(
                new CallToolRequest("get_customer_details", Map.of("customerId", stored.getId())));

        assertThat(result.isError()).isFalse();
        JsonNode customer = objectMapper.readTree(resultText(result));
        assertThat(customer.get("id").asLong()).isEqualTo(stored.getId());
        assertIso8601Instant(customer.get("createdAt"), stored.getCreatedAt());
        assertIso8601Instant(customer.get("updatedAt"), stored.getUpdatedAt());
    }

    @Test
    void customerList_returnsInstantsAsIso8601StringsThroughRealTransport() throws Exception {
        storeCustomer();

        CallToolResult result = client.callTool(new CallToolRequest("get_customer_list", Map.of("page", 0, "size", 100)));

        assertThat(result.isError()).isFalse();
        JsonNode customers = objectMapper.readTree(resultText(result)).get("content");
        assertThat(customers).isNotEmpty();
        customers.forEach(customer -> {
            assertThat(customer.get("createdAt").isTextual()).isTrue();
            assertThat(customer.get("updatedAt").isTextual()).isTrue();
            assertThat(Instant.parse(customer.get("createdAt").asText())).isNotNull();
            assertThat(Instant.parse(customer.get("updatedAt").asText())).isNotNull();
        });
    }

    @Test
    void concurrentCallsOnOneSession_eachErrorCarriesItsOwnCorrelationId() throws Exception {
        int calls = 24;
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (int i = 0; i < calls; i++) {
                String correlationId = "concurrent-" + i;
                // Alternate between an argument error and an error raised by the service after a database query.
                Callable<JsonNode> call = i % 2 == 0
                        ? () -> errorJson(callWithCorrelationId(correlationId, "get_customer_list", INVALID_PAGE_ARGUMENTS))
                        : () -> errorJson(callWithCorrelationId(
                                correlationId, "get_customer_details", Map.of("customerId", Long.MAX_VALUE)));
                futures.add(callers.submit(call));
            }

            for (int i = 0; i < calls; i++) {
                JsonNode error = futures.get(i).get(30, TimeUnit.SECONDS);
                assertThat(error.get("correlationId").asText()).isEqualTo("concurrent-" + i);
                assertThat(error.get("errorCode").asText())
                        .isEqualTo(i % 2 == 0 ? "INVALID_PAGE" : "CUSTOMER_NOT_FOUND");
            }
        } finally {
            callers.shutdownNow();
        }
    }

    /**
     * Stores a customer with unique identifiers, since the PostgreSQL container is shared by all integration
     * tests, and returns it as re-read from the database so its timestamps carry the stored precision.
     */
    private Customer storeCustomer() {
        String suffix = UUID.randomUUID().toString();
        Customer saved = customerRepository.saveAndFlush(
                new Customer("MCP-" + suffix, "Iso", "Instant", "mcp-" + suffix + "@example.com"));
        storedCustomerIds.add(saved.getId());
        return customerRepository.findById(saved.getId()).orElseThrow();
    }

    private static void assertIso8601Instant(JsonNode value, Instant expected) {
        assertThat(value.isTextual()).as("expected an ISO-8601 string but was %s", value).isTrue();
        assertThat(Instant.parse(value.asText())).isEqualTo(expected);
    }

    private CallToolResult callWithCorrelationId(String correlationId, String toolName, Map<String, Object> arguments) {
        outgoingCorrelationId.set(correlationId);
        try {
            return client.callTool(new CallToolRequest(toolName, arguments));
        } finally {
            outgoingCorrelationId.remove();
        }
    }

    private JsonNode errorJson(CallToolResult result) throws Exception {
        return objectMapper.readTree(resultText(result));
    }

    private static String resultText(CallToolResult result) {
        assertThat(result.content()).singleElement().isInstanceOf(TextContent.class);
        return ((TextContent) result.content().get(0)).text();
    }
}
