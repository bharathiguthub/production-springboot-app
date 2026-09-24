package com.example.productionapp.service;

import com.example.productionapp.dto.RedemptionResponse;
import com.example.productionapp.entity.Redemption;
import com.example.productionapp.entity.RedemptionStatus;
import com.example.productionapp.exception.RedemptionNotFoundException;
import com.example.productionapp.mapper.RedemptionMapper;
import com.example.productionapp.repository.RedemptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedemptionServiceTest {

    @Mock
    private RedemptionRepository redemptionRepository;

    @Mock
    private RedemptionMapper redemptionMapper;

    @InjectMocks
    private RedemptionService redemptionService;

    @Test
    void getRedemptionByRedemptionId_returnsMappedResponse_whenFound() {
        Redemption entity = new Redemption(
                "RDM-1001", 1L, "PAYPAL", 5000L, new BigDecimal("50.00"), "USD", RedemptionStatus.FAILED, "VENDOR_TIMEOUT");
        RedemptionResponse expected = new RedemptionResponse(
                1L, "RDM-1001", 1L, "PAYPAL", 5000L, new BigDecimal("50.00"), "USD",
                RedemptionStatus.FAILED, "VENDOR_TIMEOUT", Instant.now(), Instant.now());
        when(redemptionRepository.findByRedemptionId("RDM-1001")).thenReturn(Optional.of(entity));
        when(redemptionMapper.toResponse(entity)).thenReturn(expected);

        RedemptionResponse result = redemptionService.getRedemptionByRedemptionId("RDM-1001");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getRedemptionByRedemptionId_throwsNotFound_whenMissing() {
        when(redemptionRepository.findByRedemptionId("RDM-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> redemptionService.getRedemptionByRedemptionId("RDM-9999"))
                .isInstanceOf(RedemptionNotFoundException.class)
                .hasMessage("Redemption not found with redemptionId: RDM-9999");

        verifyNoInteractions(redemptionMapper);
    }
}
