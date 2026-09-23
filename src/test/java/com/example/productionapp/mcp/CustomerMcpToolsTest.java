package com.example.productionapp.mcp;

import com.example.productionapp.dto.CustomerResponse;
import com.example.productionapp.dto.PageResponse;
import com.example.productionapp.exception.CustomerNotFoundException;
import com.example.productionapp.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void getCustomerList_delegatesRequestedPageAndSizeToCustomerService() {
        when(customerService.getAllCustomers(any(Pageable.class))).thenReturn(Page.empty());

        customerMcpTools.getCustomerList(2, 15);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(customerService).getAllCustomers(pageableCaptor.capture());
        verifyNoMoreInteractions(customerService);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(15);
    }

    @Test
    void getCustomerList_propagatesCustomerPageFromCustomerService() {
        CustomerResponse customer = new CustomerResponse(
                1L, "CUST-1", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        Page<CustomerResponse> servicePage = new PageImpl<>(List.of(customer), PageRequest.of(1, 10), 11);
        when(customerService.getAllCustomers(PageRequest.of(1, 10))).thenReturn(servicePage);

        PageResponse<CustomerResponse> result = customerMcpTools.getCustomerList(1, 10);

        assertThat(result).isEqualTo(PageResponse.from(servicePage));
        assertThat(result.content()).containsExactly(customer);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(11);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.last()).isTrue();
    }
}
