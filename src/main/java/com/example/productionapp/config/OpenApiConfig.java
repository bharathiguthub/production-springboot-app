package com.example.productionapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productionApplicationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Production Spring Boot Application API")
                        .description("Customer management API")
                        .version("v1"));
    }
}
