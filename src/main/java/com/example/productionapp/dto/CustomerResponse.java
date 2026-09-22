package com.example.productionapp.dto;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String customerNumber,
        String firstName,
        String lastName,
        String email,
        Instant createdAt,
        Instant updatedAt
) {
}
