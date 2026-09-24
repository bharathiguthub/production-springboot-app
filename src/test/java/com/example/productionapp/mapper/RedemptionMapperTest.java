package com.example.productionapp.mapper;

import com.example.productionapp.dto.RedemptionResponse;
import com.example.productionapp.entity.Redemption;
import com.example.productionapp.entity.RedemptionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RedemptionMapperTest {

    private final RedemptionMapper redemptionMapper = new RedemptionMapper();

    @Test
    void toResponse_copiesEveryField() {
        Instant createdAt = Instant.parse("2026-09-20T10:15:00Z");
        Instant updatedAt = Instant.parse("2026-09-20T10:15:30Z");
        Redemption redemption = new Redemption(
                "RDM-1001", 7L, "PAYPAL", 5000L, new BigDecimal("50.00"), "USD", RedemptionStatus.FAILED, "VENDOR_TIMEOUT");
        // id and timestamps are assigned by the database and JPA callbacks, which do not run in a unit test.
        ReflectionTestUtils.setField(redemption, "id", 42L);
        ReflectionTestUtils.setField(redemption, "createdAt", createdAt);
        ReflectionTestUtils.setField(redemption, "updatedAt", updatedAt);

        RedemptionResponse response = redemptionMapper.toResponse(redemption);

        assertThat(response).isEqualTo(new RedemptionResponse(
                42L, "RDM-1001", 7L, "PAYPAL", 5000L, new BigDecimal("50.00"), "USD",
                RedemptionStatus.FAILED, "VENDOR_TIMEOUT", createdAt, updatedAt));
    }
}
