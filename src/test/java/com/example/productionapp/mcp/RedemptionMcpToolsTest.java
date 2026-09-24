package com.example.productionapp.mcp;

import com.example.productionapp.dto.RedemptionResponse;
import com.example.productionapp.entity.RedemptionStatus;
import com.example.productionapp.exception.RedemptionNotFoundException;
import com.example.productionapp.service.RedemptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedemptionMcpToolsTest {

    @Mock
    private RedemptionService redemptionService;

    @InjectMocks
    private RedemptionMcpTools redemptionMcpTools;

    @Test
    void getRedemptionDetails_delegatesToRedemptionService_andReturnsItsResponse() {
        RedemptionResponse expected = new RedemptionResponse(
                1L, "RDM-1001", 1L, "PAYPAL", 5000L, new BigDecimal("50.00"), "USD",
                RedemptionStatus.FAILED, "VENDOR_TIMEOUT", Instant.now(), Instant.now());
        when(redemptionService.getRedemptionByRedemptionId("RDM-1001")).thenReturn(expected);

        RedemptionResponse result = redemptionMcpTools.getRedemptionDetails("RDM-1001");

        assertThat(result).isEqualTo(expected);
        verify(redemptionService).getRedemptionByRedemptionId("RDM-1001");
        verifyNoMoreInteractions(redemptionService);
    }

    @Test
    void getRedemptionDetails_propagatesNotFoundException_whenRedemptionMissing() {
        when(redemptionService.getRedemptionByRedemptionId("RDM-9999"))
                .thenThrow(new RedemptionNotFoundException("Redemption not found with redemptionId: RDM-9999"));

        assertThatThrownBy(() -> redemptionMcpTools.getRedemptionDetails("RDM-9999"))
                .isInstanceOf(RedemptionNotFoundException.class);
    }

    @Test
    void getRedemptionDetails_rejectsNullRedemptionId_withoutCallingRedemptionService() {
        assertThatThrownBy(() -> redemptionMcpTools.getRedemptionDetails(null))
                .isInstanceOf(McpToolException.class)
                .hasFieldOrPropertyWithValue("errorCode", McpToolErrorCode.INVALID_ARGUMENTS)
                .hasMessage("redemptionId is required");

        verifyNoInteractions(redemptionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void getRedemptionDetails_rejectsBlankRedemptionId_withoutCallingRedemptionService(String redemptionId) {
        assertThatThrownBy(() -> redemptionMcpTools.getRedemptionDetails(redemptionId))
                .isInstanceOf(McpToolException.class)
                .hasFieldOrPropertyWithValue("errorCode", McpToolErrorCode.INVALID_ARGUMENTS)
                .hasMessage("redemptionId must not be blank");

        verifyNoInteractions(redemptionService);
    }

    @Test
    void getRedemptionDetails_rejectsRedemptionIdLongerThan64Characters_withoutCallingRedemptionService() {
        String tooLong = "R".repeat(65);

        assertThatThrownBy(() -> redemptionMcpTools.getRedemptionDetails(tooLong))
                .isInstanceOf(McpToolException.class)
                .hasFieldOrPropertyWithValue("errorCode", McpToolErrorCode.INVALID_ARGUMENTS)
                .hasMessage("redemptionId must be at most 64 characters");

        verifyNoInteractions(redemptionService);
    }

    @Test
    void getRedemptionDetails_acceptsRedemptionIdOfExactly64Characters() {
        String maxLength = "R".repeat(64);

        redemptionMcpTools.getRedemptionDetails(maxLength);

        verify(redemptionService).getRedemptionByRedemptionId(maxLength);
    }

    @Test
    void dependsOnlyOnRedemptionService() {
        assertThat(RedemptionMcpTools.class.getDeclaredConstructors()).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(RedemptionService.class));
    }
}
