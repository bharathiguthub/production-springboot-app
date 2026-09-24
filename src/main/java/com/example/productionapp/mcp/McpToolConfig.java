package com.example.productionapp.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider customerToolCallbackProvider(CustomerMcpTools customerMcpTools, ObjectMapper objectMapper) {
        return withErrorHandling(customerMcpTools, objectMapper);
    }

    // Spring AI's MCP server auto-configuration registers the tools of every ToolCallbackProvider bean.
    @Bean
    public ToolCallbackProvider redemptionToolCallbackProvider(RedemptionMcpTools redemptionMcpTools, ObjectMapper objectMapper) {
        return withErrorHandling(redemptionMcpTools, objectMapper);
    }

    private static ToolCallbackProvider withErrorHandling(Object toolObject, ObjectMapper objectMapper) {
        ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
                .toolObjects(toolObject)
                .build()
                .getToolCallbacks();

        return ToolCallbackProvider.from(Arrays.stream(toolCallbacks)
                .map(toolCallback -> new McpToolErrorHandlingToolCallback(toolCallback, objectMapper))
                .toList());
    }
}
