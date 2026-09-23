package com.example.productionapp.mcp;

import com.example.productionapp.dto.CustomerResponse;
import com.example.productionapp.exception.CustomerNotFoundException;
import com.example.productionapp.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerMcpToolsTest {

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private CustomerMcpTools customerMcpTools;

    @Test
    void getCustomerDetails_delegatesToCustomerService_andReturnsItsResponse() {
        CustomerResponse expected = new CustomerResponse(
                1L, "CUST-1", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        when(customerService.getCustomerById(1L)).thenReturn(expected);

        CustomerResponse result = customerMcpTools.getCustomerDetails(1L);

        assertThat(result).isEqualTo(expected);
        verify(customerService).getCustomerById(1L);
        verifyNoMoreInteractions(customerService);
    }

    @Test
    void getCustomerDetails_propagatesNotFoundException_whenCustomerMissing() {
        when(customerService.getCustomerById(99L)).thenThrow(new CustomerNotFoundException("Customer not found with id: 99"));

        assertThatThrownBy(() -> customerMcpTools.getCustomerDetails(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
