package com.example.productionapp.mcp;

import com.example.productionapp.dto.CustomerResponse;
import com.example.productionapp.dto.PageResponse;
import com.example.productionapp.service.CustomerService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class CustomerMcpTools {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerService customerService;

    public CustomerMcpTools(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Tool(name = "get_customer_details", description = "Retrieve customer details by customer ID.")
    public CustomerResponse getCustomerDetails(
            @ToolParam(description = "The positive customer ID of the customer to retrieve, must be greater than 0")
            Long customerId) {
        // Spring AI binds a missing or JSON-null argument as null, so it reaches this method.
        if (customerId == null) {
            throw new McpToolException(McpToolErrorCode.INVALID_ARGUMENTS, "customerId is required");
        }
        if (customerId <= 0) {
            throw new McpToolException(McpToolErrorCode.INVALID_ARGUMENTS, "customerId must be greater than 0");
        }
        return customerService.getCustomerById(customerId);
    }

    @Tool(name = "get_customer_list", description = "Retrieve a paginated list of customers.")
    public PageResponse<CustomerResponse> getCustomerList(
            @ToolParam(description = "Zero-based page index, must be >= 0") int page,
            @ToolParam(description = "Number of customers per page, between 1 and 100") int size) {
        if (page < 0) {
            throw new McpToolException(McpToolErrorCode.INVALID_PAGE, "page must be greater than or equal to 0");
        }
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new McpToolException(McpToolErrorCode.INVALID_PAGE_SIZE, "size must be between 1 and 100");
        }
        // Page itself cannot be returned: Spring AI skips @Tool methods whose return type is a
        // java.util.function type, and Page is a Supplier via Streamable.
        return PageResponse.from(customerService.getAllCustomers(PageRequest.of(page, size)));
    }
}
