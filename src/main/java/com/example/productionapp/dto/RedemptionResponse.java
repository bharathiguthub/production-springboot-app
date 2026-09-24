package com.example.productionapp.dto;

import com.example.productionapp.entity.RedemptionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record RedemptionResponse(
        Long id,
        String redemptionId,
        Long customerId,
        String vendor,
        Long points,
        BigDecimal amount,
        String currency,
        RedemptionStatus status,
        String errorCode,
        Instant createdAt,
        Instant updatedAt
) {
}
