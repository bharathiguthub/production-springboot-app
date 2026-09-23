package com.example.productionapp.mcp;

import com.example.productionapp.config.CorrelationIdFilter;
import com.example.productionapp.dto.CustomerResponse;
import com.example.productionapp.exception.CustomerNotFoundException;
import com.example.productionapp.service.CustomerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolErrorHandlingToolCallbackTest {

    private static final String CORRELATION_ID = "test-correlation-id";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

    @Mock
    private CustomerService customerService;

    private ToolCallback[] callbacks;

    @BeforeEach
    void setUp() {
        callbacks = new McpToolConfig()
                .customerToolCallbackProvider(new CustomerMcpTools(customerService), objectMapper)
                .getToolCallbacks();
    }

    @AfterEach
    void clearMdc() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    void invalidPage_returnsErrorResultWithStructuredJson() throws Exception {
        CallToolResult result = callTool("get_customer_list", Map.of("page", -1, "size", 10));

        assertThat(result.isError()).isTrue();
        JsonNode error = errorJson(result);
        assertThat(error.get("errorCode").asText()).isEqualTo("INVALID_PAGE");
        assertThat(error.get("message").asText()).isEqualTo("page must be greater than or equal to 0");
        assertThat(error.get("retryable").asBoolean()).isFalse();
        assertThat(error.has("correlationId")).isTrue();
        verifyNoInteractions(customerService);
    }

    @Test
    void invalidPageSize_returnsErrorResultWithStructuredJson() throws Exception {
        CallToolResult result = callTool("get_customer_list", Map.of("page", 0, "size", 101));

        assertThat(result.isError()).isTrue();
        JsonNode error = errorJson(result);
        assertThat(error.get("errorCode").asText()).isEqualTo("INVALID_PAGE_SIZE");
        assertThat(error.get("message").asText()).isEqualTo("size must be between 1 and 100");
        assertThat(error.get("retryable").asBoolean()).isFalse();
        verifyNoInteractions(customerService);
    }

    @Test
    void customerNotFound_isMappedToCustomerNotFoundErrorCode() throws Exception {
        when(customerService.getCustomerById(99L))
                .thenThrow(new CustomerNotFoundException("Customer not found with id: 99"));

        CallToolResult result = callTool("get_customer_details", Map.of("customerId", 99));

        assertThat(result.isError()).isTrue();
        JsonNode error = errorJson(result);
        assertThat(error.get("errorCode").asText()).isEqualTo("CUSTOMER_NOT_FOUND");
        assertThat(error.get("message").asText()).isEqualTo("Customer not found with id: 99");
        assertThat(error.get("retryable").asBoolean()).isFalse();
    }

    @Test
    void unexpectedException_isSanitizedToInternalError() throws Exception {
        when(customerService.getCustomerById(1L)).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [ERROR: relation \"customers\" does not exist] [select c1_0.id from customers c1_0]"));

        CallToolResult result = callTool("get_customer_details", Map.of("customerId", 1));

        assertThat(result.isError()).isTrue();
        String text = resultText(result);
        JsonNode error = objectMapper.readTree(text);
        assertThat(error.get("errorCode").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.get("message").asText())
                .isEqualTo(McpToolErrorHandlingToolCallback.INTERNAL_ERROR_MESSAGE);
        assertThat(error.get("retryable").asBoolean()).isFalse();
        assertThat(text)
                .doesNotContain("select")
                .doesNotContain("does not exist")
                .doesNotContain("c1_0")
                .doesNotContain("DataIntegrityViolationException")
                .doesNotContain("org.springframework")
                .doesNotContain("at com.example");
    }

    @Test
    void malformedArgument_isMappedToInvalidArguments() throws Exception {
        CallToolResult result = callTool("get_customer_details", Map.of("customerId", "not-a-number"));

        assertThat(result.isError()).isTrue();
        String text = resultText(result);
        JsonNode error = objectMapper.readTree(text);
        assertThat(error.get("errorCode").asText()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(error.get("message").asText())
                .isEqualTo(McpToolErrorHandlingToolCallback.INVALID_ARGUMENTS_MESSAGE);
        assertThat(text).doesNotContain("not-a-number").doesNotContain("Exception");
        verifyNoInteractions(customerService);
    }

    @Test
    void missingPrimitiveArgument_isMappedToInvalidArguments() throws Exception {
        CallToolResult result = callTool("get_customer_list", Map.of("page", 0));

        assertThat(result.isError()).isTrue();
        assertThat(errorJson(result).get("errorCode").asText()).isEqualTo("INVALID_ARGUMENTS");
        verifyNoInteractions(customerService);
    }

    @Test
    void missingCustomerId_reachesToolMethodAndReturnsInvalidArguments() throws Exception {
        assertInvalidCustomerId(Map.of(), "customerId is required");
    }

    @Test
    void explicitNullCustomerId_reachesToolMethodAndReturnsInvalidArguments() throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("customerId", null);

        assertInvalidCustomerId(arguments, "customerId is required");
    }

    @Test
    void zeroCustomerId_returnsInvalidArguments() throws Exception {
        assertInvalidCustomerId(Map.of("customerId", 0), "customerId must be greater than 0");
    }

    @Test
    void negativeCustomerId_returnsInvalidArguments() throws Exception {
        assertInvalidCustomerId(Map.of("customerId", -1), "customerId must be greater than 0");
    }

    @Test
    void fractionalCustomerId_isRejectedDuringBindingAsInvalidArguments() throws Exception {
        CallToolResult result = callTool("get_customer_details", Map.of("customerId", 1.5));

        assertThat(result.isError()).isTrue();
        JsonNode error = errorJson(result);
        assertThat(error.get("errorCode").asText()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(error.get("message").asText())
                .isEqualTo(McpToolErrorHandlingToolCallback.INVALID_ARGUMENTS_MESSAGE);
        verifyNoInteractions(customerService);
    }

    private void assertInvalidCustomerId(Map<String, Object> arguments, String expectedMessage) throws Exception {
        CallToolResult result = callTool("get_customer_details", arguments);

        assertThat(result.isError()).isTrue();
        JsonNode error = errorJson(result);
        assertThat(error.get("errorCode").asText()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(error.get("message").asText()).isEqualTo(expectedMessage);
        assertThat(error.get("retryable").asBoolean()).isFalse();
        verifyNoInteractions(customerService);
    }

    @Test
    void correlationId_isReadFromTransportContext() throws Exception {
        when(exchange.transportContext()).thenReturn(transportContextWith(CORRELATION_ID));

        CallToolResult result = callTool("get_customer_list", Map.of("page", -1, "size", 10));

        assertThat(errorJson(result).get("correlationId").asText()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void correlationId_isNullWhenTransportContextIsEmpty() throws Exception {
        when(exchange.transportContext()).thenReturn(McpTransportContext.EMPTY);

        CallToolResult result = callTool("get_customer_list", Map.of("page", -1, "size", 10));

        assertThat(errorJson(result).get("correlationId").isNull()).isTrue();
    }

    @Test
    void correlationId_isNullWhenExchangeHasNoTransportContext() throws Exception {
        CallToolResult result = callTool("get_customer_list", Map.of("page", -1, "size", 10));

        assertThat(errorJson(result).get("correlationId").isNull()).isTrue();
    }

    @Test
    void correlationId_isNullWhenCalledWithoutToolContext() throws Exception {
        RuntimeException thrown = catchRuntimeException(
                () -> findByName(callbacks, "get_customer_list").call("{\"page\": -1, \"size\": 10}"));

        assertThat(objectMapper.readTree(thrown.getMessage()).get("correlationId").isNull()).isTrue();
    }

    @Test
    void correlationId_isNullWhenToolContextHasNoExchange() throws Exception {
        RuntimeException thrown = catchRuntimeException(() -> findByName(callbacks, "get_customer_list")
                .call("{\"page\": -1, \"size\": 10}", new ToolContext(Map.of())));

        assertThat(objectMapper.readTree(thrown.getMessage()).get("correlationId").isNull()).isTrue();
    }

    @Test
    void correlationId_ignoresStaleMdcValueOnExecutingThread() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, "stale-correlation-id");

        CallToolResult result = callTool("get_customer_list", Map.of("page", -1, "size", 10));

        assertThat(errorJson(result).get("correlationId").isNull()).isTrue();
    }

    @Test
    void correlationId_isInMdcWhileToolExecutes() {
        when(exchange.transportContext()).thenReturn(transportContextWith(CORRELATION_ID));
        AtomicReference<String> mdcDuringCall = new AtomicReference<>();
        when(customerService.getCustomerById(7L)).thenAnswer(invocation -> {
            mdcDuringCall.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return customer(7L);
        });

        CallToolResult result = callTool("get_customer_details", Map.of("customerId", 7));

        assertThat(result.isError()).isFalse();
        assertThat(mdcDuringCall).hasValue(CORRELATION_ID);
    }

    @Test
    void mdcIsRemovedAfterSuccessfulCall() {
        when(exchange.transportContext()).thenReturn(transportContextWith(CORRELATION_ID));
        when(customerService.getCustomerById(7L)).thenReturn(customer(7L));

        callTool("get_customer_details", Map.of("customerId", 7));

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void mdcIsRemovedAfterFailedCall() throws Exception {
        when(exchange.transportContext()).thenReturn(transportContextWith(CORRELATION_ID));
        AtomicReference<String> mdcDuringCall = new AtomicReference<>();
        when(customerService.getCustomerById(99L)).thenAnswer(invocation -> {
            mdcDuringCall.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            throw new CustomerNotFoundException("Customer not found with id: 99");
        });

        CallToolResult result = callTool("get_customer_details", Map.of("customerId", 99));

        assertThat(errorJson(result).get("correlationId").asText()).isEqualTo(CORRELATION_ID);
        assertThat(mdcDuringCall).hasValue(CORRELATION_ID);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void previousMdcValueIsRestoredAfterCall() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "outer-correlation-id");
        when(exchange.transportContext()).thenReturn(transportContextWith(CORRELATION_ID));
        AtomicReference<String> mdcDuringCall = new AtomicReference<>();
        when(customerService.getCustomerById(7L)).thenAnswer(invocation -> {
            mdcDuringCall.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return customer(7L);
        });

        callTool("get_customer_details", Map.of("customerId", 7));

        assertThat(mdcDuringCall).hasValue(CORRELATION_ID);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("outer-correlation-id");
    }

    /**
     * Regression test for the real MCP execution path: the SDK runs tools on a worker thread, so a
     * correlation ID that exists only in the request thread's MDC must not be what reaches the tool.
     */
    @Test
    void correlationId_isPropagatedToToolExecutedOnAnotherThread() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, "request-thread-only-id");
        when(exchange.transportContext()).thenReturn(transportContextWith(CORRELATION_ID));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CallToolResult result = worker
                    .submit(() -> callTool("get_customer_list", Map.of("page", -1, "size", 10)))
                    .get(5, TimeUnit.SECONDS);

            assertThat(errorJson(result).get("correlationId").asText()).isEqualTo(CORRELATION_ID);
            assertThat(worker.submit(() -> MDC.get(CorrelationIdFilter.MDC_KEY)).get(5, TimeUnit.SECONDS))
                    .isNull();
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void reusedWorkerThread_doesNotLeakCorrelationIdIntoNextCall() throws Exception {
        McpSyncServerExchange firstExchange = mock(McpSyncServerExchange.class);
        McpSyncServerExchange secondExchange = mock(McpSyncServerExchange.class);
        when(firstExchange.transportContext()).thenReturn(transportContextWith("first-correlation-id"));
        when(secondExchange.transportContext()).thenReturn(McpTransportContext.EMPTY);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CallToolResult first = worker
                    .submit(() -> callTool(firstExchange, "get_customer_list", Map.of("page", -1, "size", 10)))
                    .get(5, TimeUnit.SECONDS);
            CallToolResult second = worker
                    .submit(() -> callTool(secondExchange, "get_customer_list", Map.of("page", -1, "size", 10)))
                    .get(5, TimeUnit.SECONDS);

            assertThat(errorJson(first).get("correlationId").asText()).isEqualTo("first-correlation-id");
            assertThat(errorJson(second).get("correlationId").isNull()).isTrue();
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void errorResponseException_carriesOnlyTheJsonMessage() {
        RuntimeException thrown = catchRuntimeException(
                () -> findByName(callbacks, "get_customer_list").call("{\"page\": -1, \"size\": 10}"));

        assertThat(thrown).isInstanceOf(McpToolErrorHandlingToolCallback.McpToolErrorResponseException.class);
        assertThat(thrown.getCause()).isNull();
        assertThat(thrown.getStackTrace()).isEmpty();
        assertThat(thrown.getMessage()).startsWith("{\"errorCode\":\"INVALID_PAGE\"");
    }

    @Test
    void successfulCalls_returnExactlyWhatTheUndecoratedCallbackReturns() {
        CustomerResponse customer = new CustomerResponse(
                7L, "CUST-7", "Jane", "Doe", "jane.doe@example.com",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));
        Page<CustomerResponse> page = new PageImpl<>(List.of(customer), PageRequest.of(0, 10), 1);
        when(customerService.getCustomerById(7L)).thenReturn(customer);
        when(customerService.getAllCustomers(PageRequest.of(0, 10))).thenReturn(page);
        ToolCallback[] undecorated = MethodToolCallbackProvider.builder()
                .toolObjects(new CustomerMcpTools(customerService))
                .build()
                .getToolCallbacks();

        String detailsInput = "{\"customerId\": 7}";
        String listInput = "{\"page\": 0, \"size\": 10}";

        assertThat(findByName(callbacks, "get_customer_details").call(detailsInput))
                .isEqualTo(findByName(undecorated, "get_customer_details").call(detailsInput));
        assertThat(findByName(callbacks, "get_customer_list").call(listInput))
                .isEqualTo(findByName(undecorated, "get_customer_list").call(listInput));
    }

    @Test
    void successfulCall_producesNonErrorMcpResult() {
        CustomerResponse customer = new CustomerResponse(
                7L, "CUST-7", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        when(customerService.getCustomerById(7L)).thenReturn(customer);

        CallToolResult result = callTool("get_customer_details", Map.of("customerId", 7));

        assertThat(result.isError()).isFalse();
        assertThat(resultText(result)).contains("\"customerNumber\":\"CUST-7\"");
    }

    private CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        return callTool(exchange, toolName, arguments);
    }

    private CallToolResult callTool(McpSyncServerExchange toolExchange, String toolName, Map<String, Object> arguments) {
        return McpToolUtils.toSyncToolSpecification(findByName(callbacks, toolName))
                .callHandler()
                .apply(toolExchange, new CallToolRequest(toolName, arguments));
    }

    private static McpTransportContext transportContextWith(String correlationId) {
        return McpTransportContext.create(
                Map.of(CorrelationIdTransportContextExtractor.CORRELATION_ID_KEY, correlationId));
    }

    private static CustomerResponse customer(Long id) {
        return new CustomerResponse(
                id, "CUST-" + id, "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
    }

    private JsonNode errorJson(CallToolResult result) throws Exception {
        return objectMapper.readTree(resultText(result));
    }

    private static String resultText(CallToolResult result) {
        assertThat(result.content()).singleElement().isInstanceOf(TextContent.class);
        return ((TextContent) result.content().get(0)).text();
    }

    private static ToolCallback findByName(ToolCallback[] callbacks, String name) {
        return Arrays.stream(callbacks)
                .filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not registered: " + name));
    }
}
