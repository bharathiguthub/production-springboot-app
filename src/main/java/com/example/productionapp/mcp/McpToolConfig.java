package com.example.productionapp.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider customerToolCallbackProvider(CustomerMcpTools customerMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(customerMcpTools)
                .build();
    }
}
