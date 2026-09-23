package com.example.productionapp.mcp;

import com.example.productionapp.AbstractIntegrationTest;
import com.example.productionapp.config.CorrelationIdFilter;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
