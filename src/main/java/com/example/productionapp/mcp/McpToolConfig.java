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
        ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
                .toolObjects(customerMcpTools)
                .build()
                .getToolCallbacks();

        return ToolCallbackProvider.from(Arrays.stream(toolCallbacks)
                .map(toolCallback -> new McpToolErrorHandlingToolCallback(toolCallback, objectMapper))
                .toList());
    }
}
