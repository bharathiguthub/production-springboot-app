package com.example.productionapp.mcp;

import com.example.productionapp.dto.CustomerResponse;
import com.example.productionapp.service.CustomerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerMcpToolsToolCallbackTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-21T23:20:00.443326Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-09-22T01:49:54.430074Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CustomerService customerService;

    private ToolCallback customerDetailsCallback;

    private ToolCallback customerListCallback;

    @BeforeEach
    void setUp() {
        CustomerMcpTools customerMcpTools = new CustomerMcpTools(customerService);
        ToolCallback[] callbacks = new McpToolConfig()
                .customerToolCallbackProvider(customerMcpTools, new ObjectMapper())
                .getToolCallbacks();

        assertThat(callbacks).hasSize(2);
        customerDetailsCallback = findByName(callbacks, "get_customer_details");
        customerListCallback = findByName(callbacks, "get_customer_list");
    }

    private static ToolCallback findByName(ToolCallback[] callbacks, String name) {
        return Arrays.stream(callbacks)
                .filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not registered: " + name));
    }

    @Test
    void toolDefinition_isRegisteredWithExpectedNameAndDescription() {
        assertThat(customerDetailsCallback.getToolDefinition().name()).isEqualTo("get_customer_details");
        assertThat(customerDetailsCallback.getToolDefinition().description()).isEqualTo("Retrieve customer details by customer ID.");
        assertThat(customerDetailsCallback.getToolDefinition().inputSchema())
                .contains("customerId")
                .contains("The positive customer ID of the customer to retrieve, must be greater than 0");
    }

    @Test
    void call_invokesCustomerServiceAndReturnsJsonWithCustomerData() {
        CustomerResponse response = new CustomerResponse(
                42L, "CUST-42", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        when(customerService.getCustomerById(42L)).thenReturn(response);

        String result = customerDetailsCallback.call("{\"customerId\": 42}");

        assertThat(result).contains("\"customerNumber\":\"CUST-42\"");
        assertThat(result).contains("\"firstName\":\"Jane\"");
    }

    @Test
    void customerListToolDefinition_isRegisteredWithPageAndSizeInputs() {
        assertThat(customerListCallback.getToolDefinition().description())
                .isEqualTo("Retrieve a paginated list of customers.");
        assertThat(customerListCallback.getToolDefinition().inputSchema())
                .contains("\"page\"")
                .contains("\"size\"");
    }

    @Test
    void customerDetailsCall_writesInstantsAsIso8601Strings() throws Exception {
        when(customerService.getCustomerById(1L)).thenReturn(customerWithTimestamps(1L));

        JsonNode customer = objectMapper.readTree(customerDetailsCallback.call("{\"customerId\": 1}"));

        assertIso8601Timestamps(customer);
    }

    @Test
    void customerListCall_writesInstantsOfEveryCustomerAsIso8601Strings() throws Exception {
        PageRequest pageRequest = PageRequest.of(0, 5);
        when(customerService.getAllCustomers(pageRequest)).thenReturn(new PageImpl<>(
                List.of(customerWithTimestamps(1L), customerWithTimestamps(2L)), pageRequest, 2));

        JsonNode page = objectMapper.readTree(customerListCallback.call("{\"page\": 0, \"size\": 5}"));

        assertThat(page.get("content")).hasSize(2);
        page.get("content").forEach(CustomerMcpToolsToolCallbackTest::assertIso8601Timestamps);
        assertThat(page.get("totalElements").asLong()).isEqualTo(2L);
    }

    private static CustomerResponse customerWithTimestamps(long id) {
        return new CustomerResponse(
                id, "CUST-" + id, "Jane", "Doe", "jane.doe" + id + "@example.com", CREATED_AT, UPDATED_AT);
    }

    private static void assertIso8601Timestamps(JsonNode customer) {
        assertThat(customer.get("createdAt").isTextual()).isTrue();
        assertThat(customer.get("createdAt").asText()).isEqualTo("2026-09-21T23:20:00.443326Z");
        assertThat(customer.get("updatedAt").isTextual()).isTrue();
        assertThat(customer.get("updatedAt").asText()).isEqualTo("2026-09-22T01:49:54.430074Z");
    }
}
