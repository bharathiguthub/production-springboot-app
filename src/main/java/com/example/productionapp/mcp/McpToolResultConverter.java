package com.example.productionapp.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.io.UncheckedIOException;
import java.lang.reflect.Type;

/**
 * Serializes MCP tool results to JSON, writing {@code java.time} values such as {@link java.time.Instant} as
 * ISO-8601 strings, consistent with the REST API.
 * <p>
 * Spring AI's default converter uses its own static {@code ObjectMapper}, which leaves
 * {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} enabled and so renders an {@code Instant} as epoch
 * seconds. Spring AI instantiates the converter named by {@code @Tool(resultConverter = ...)} through its
 * no-argument constructor, so the application's {@code ObjectMapper} bean cannot be injected here and the
 * relevant date handling is configured explicitly instead.
 */
public class McpToolResultConverter implements ToolCallResultConverter {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Override
    public String convert(Object result, Type returnType) {
        try {
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            // Deliberately not an IllegalArgumentException/IllegalStateException: conversion runs outside the
            // tool method, and McpToolErrorHandlingToolCallback reports those as invalid arguments rather than
            // as the internal error this is.
            throw new UncheckedIOException("Failed to serialize MCP tool result", ex);
        }
    }
}
