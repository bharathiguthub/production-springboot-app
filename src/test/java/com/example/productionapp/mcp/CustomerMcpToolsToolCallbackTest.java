package com.example.productionapp.mcp;

import com.example.productionapp.dto.CustomerResponse;
import com.example.productionapp.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerMcpToolsToolCallbackTest {

    @Mock
    private CustomerService customerService;

    private ToolCallback toolCallback;

    @BeforeEach
    void setUp() {
        CustomerMcpTools customerMcpTools = new CustomerMcpTools(customerService);
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(customerMcpTools)
                .build()
                .getToolCallbacks();

        assertThat(callbacks).hasSize(1);
        toolCallback = callbacks[0];
    }

    @Test
    void toolDefinition_isRegisteredWithExpectedNameAndDescription() {
        assertThat(toolCallback.getToolDefinition().name()).isEqualTo("get_customer_details");
        assertThat(toolCallback.getToolDefinition().description()).isEqualTo("Retrieve customer details by customer ID.");
        assertThat(toolCallback.getToolDefinition().inputSchema()).contains("customerId");
    }

    @Test
    void call_invokesCustomerServiceAndReturnsJsonWithCustomerData() {
        CustomerResponse response = new CustomerResponse(
                42L, "CUST-42", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        when(customerService.getCustomerById(42L)).thenReturn(response);

        String result = toolCallback.call("{\"customerId\": 42}");

        assertThat(result).contains("\"customerNumber\":\"CUST-42\"");
        assertThat(result).contains("\"firstName\":\"Jane\"");
    }
}
