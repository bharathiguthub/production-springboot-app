package com.example.productionapp.mcp;

import com.example.productionapp.dto.CustomerResponse;
import com.example.productionapp.service.CustomerService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class CustomerMcpTools {

    private final CustomerService customerService;

    public CustomerMcpTools(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Tool(name = "get_customer_details", description = "Retrieve customer details by customer ID.")
    public CustomerResponse getCustomerDetails(
            @ToolParam(description = "The unique identifier of the customer to retrieve") Long customerId) {
        return customerService.getCustomerById(customerId);
    }
}
