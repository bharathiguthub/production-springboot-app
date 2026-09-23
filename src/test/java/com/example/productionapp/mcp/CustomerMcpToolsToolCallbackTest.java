package com.example.productionapp.mcp;

import com.example.productionapp.dto.CustomerResponse;
import com.example.productionapp.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerMcpToolsToolCallbackTest {

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
}
