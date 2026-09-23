package com.example.productionapp.mcp;

import com.example.productionapp.dto.CustomerResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpToolResultConverterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-21T23:20:00.443326Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-09-22T01:49:54.430074Z");

    private final McpToolResultConverter converter = new McpToolResultConverter();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void convert_writesInstantsAsIso8601StringsWithFullPrecision() throws Exception {
        CustomerResponse customer = new CustomerResponse(
                1L, "CUST-1001", "John", "Smith", "john.smith@example.com", CREATED_AT, UPDATED_AT);

        JsonNode json = objectMapper.readTree(converter.convert(customer, CustomerResponse.class));

        assertThat(json.get("createdAt").isTextual()).isTrue();
        assertThat(json.get("createdAt").asText()).isEqualTo("2026-09-21T23:20:00.443326Z");
        assertThat(json.get("updatedAt").isTextual()).isTrue();
        assertThat(json.get("updatedAt").asText()).isEqualTo("2026-09-22T01:49:54.430074Z");
        assertThat(json.get("id").asLong()).isEqualTo(1L);
        assertThat(json.get("customerNumber").asText()).isEqualTo("CUST-1001");
    }

    @Test
    void convert_writesNullInstantsAsJsonNull() throws Exception {
        CustomerResponse customer = new CustomerResponse(
                1L, "CUST-1001", "John", "Smith", "john.smith@example.com", null, null);

        JsonNode json = objectMapper.readTree(converter.convert(customer, CustomerResponse.class));

        assertThat(json.get("createdAt").isNull()).isTrue();
        assertThat(json.get("updatedAt").isNull()).isTrue();
    }

    @Test
    void convert_whenSerializationFails_throwsUncheckedIOException() {
        // A bean with no serializable properties fails with FAIL_ON_EMPTY_BEANS, which stays enabled.
        Object unserializable = new Object();

        assertThatThrownBy(() -> converter.convert(unserializable, Object.class))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessage("Failed to serialize MCP tool result");
    }
}
