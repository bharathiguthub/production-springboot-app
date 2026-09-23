package com.example.productionapp.service;

import com.example.productionapp.dto.CreateCustomerRequest;
import com.example.productionapp.dto.CustomerResponse;
import com.example.productionapp.dto.UpdateCustomerRequest;
import com.example.productionapp.entity.Customer;
import com.example.productionapp.exception.CustomerNotFoundException;
import com.example.productionapp.exception.DuplicateCustomerException;
import com.example.productionapp.mapper.CustomerMapper;
import com.example.productionapp.repository.CustomerRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerMapper customerMapper;

    private SimpleMeterRegistry meterRegistry;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        customerService = new CustomerService(customerRepository, customerMapper, meterRegistry);
    }

    @Test
    void createCustomer_savesAndReturnsResponse_whenNoDuplicates() {
        CreateCustomerRequest request = new CreateCustomerRequest("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        Customer entity = new Customer("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        Customer saved = new Customer("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        CustomerResponse expectedResponse = new CustomerResponse(
                1L, "CUST-1", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());

        when(customerRepository.existsByCustomerNumber("CUST-1")).thenReturn(false);
        when(customerRepository.existsByEmail("jane.doe@example.com")).thenReturn(false);
        when(customerMapper.toEntity(request)).thenReturn(entity);
        when(customerRepository.save(entity)).thenReturn(saved);
        when(customerMapper.toResponse(saved)).thenReturn(expectedResponse);

        CustomerResponse result = customerService.createCustomer(request);

        assertThat(result).isEqualTo(expectedResponse);
        verify(customerRepository).save(entity);
    }

    @Test
    void createCustomer_throwsDuplicate_whenCustomerNumberExists() {
        CreateCustomerRequest request = new CreateCustomerRequest("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        when(customerRepository.existsByCustomerNumber("CUST-1")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(DuplicateCustomerException.class);

        verify(customerRepository, never()).save(any());
    }

    @Test
    void createCustomer_throwsDuplicate_whenEmailExists() {
        CreateCustomerRequest request = new CreateCustomerRequest("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        when(customerRepository.existsByCustomerNumber("CUST-1")).thenReturn(false);
        when(customerRepository.existsByEmail("jane.doe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(DuplicateCustomerException.class);

        verify(customerRepository, never()).save(any());
    }

    @Test
    void getCustomerById_returnsResponse_whenFound() {
        Customer customer = new Customer("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        CustomerResponse expectedResponse = new CustomerResponse(
                1L, "CUST-1", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerMapper.toResponse(customer)).thenReturn(expectedResponse);

        CustomerResponse result = customerService.getCustomerById(1L);

        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    void getCustomerById_throwsNotFound_whenMissing() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomerById(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void getAllCustomers_returnsMappedPage() {
        Customer customer = new Customer("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        CustomerResponse response = new CustomerResponse(
                1L, "CUST-1", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        Pageable pageable = PageRequest.of(0, 20);
        Page<Customer> customerPage = new PageImpl<>(List.of(customer), pageable, 1);
        when(customerRepository.findAll(pageable)).thenReturn(customerPage);
        when(customerMapper.toResponse(customer)).thenReturn(response);

        Page<CustomerResponse> result = customerService.getAllCustomers(pageable);

        assertThat(result.getContent()).containsExactly(response);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void updateCustomer_updatesAndReturnsResponse_whenNoConflict() {
        Customer customer = new Customer("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        UpdateCustomerRequest request = new UpdateCustomerRequest("Jane", "Smith", "jane.smith@example.com");
        CustomerResponse expectedResponse = new CustomerResponse(
                1L, "CUST-1", "Jane", "Smith", "jane.smith@example.com", Instant.now(), Instant.now());

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.existsByEmailAndIdNot("jane.smith@example.com", 1L)).thenReturn(false);
        when(customerMapper.toResponse(customer)).thenReturn(expectedResponse);

        CustomerResponse result = customerService.updateCustomer(1L, request);

        assertThat(result).isEqualTo(expectedResponse);
        assertThat(customer.getFirstName()).isEqualTo("Jane");
        assertThat(customer.getLastName()).isEqualTo("Smith");
        assertThat(customer.getEmail()).isEqualTo("jane.smith@example.com");
        assertThat(customer.getCustomerNumber()).isEqualTo("CUST-1");
    }

    @Test
    void updateCustomer_throwsNotFound_whenMissing() {
        UpdateCustomerRequest request = new UpdateCustomerRequest("Jane", "Smith", "jane.smith@example.com");
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.updateCustomer(99L, request))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void updateCustomer_throwsDuplicate_whenEmailBelongsToAnotherCustomer() {
        Customer customer = new Customer("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        UpdateCustomerRequest request = new UpdateCustomerRequest("Jane", "Doe", "taken@example.com");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.existsByEmailAndIdNot("taken@example.com", 1L)).thenReturn(true);

        assertThatThrownBy(() -> customerService.updateCustomer(1L, request))
                .isInstanceOf(DuplicateCustomerException.class);

        assertThat(customer.getEmail()).isEqualTo("jane.doe@example.com");
    }

    @Test
    void deleteCustomer_deletes_whenExists() {
        when(customerRepository.existsById(1L)).thenReturn(true);

        customerService.deleteCustomer(1L);

        verify(customerRepository).deleteById(1L);
    }

    @Test
    void deleteCustomer_throwsNotFound_whenMissing() {
        when(customerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> customerService.deleteCustomer(99L))
                .isInstanceOf(CustomerNotFoundException.class);

        verify(customerRepository, never()).deleteById(any());
    }

    @Test
    void createCustomer_incrementsCreatedCounter_whenSuccessful() {
        CreateCustomerRequest request = new CreateCustomerRequest("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        Customer entity = new Customer("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        Customer saved = new Customer("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        CustomerResponse response = new CustomerResponse(
                1L, "CUST-1", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());

        when(customerRepository.existsByCustomerNumber("CUST-1")).thenReturn(false);
        when(customerRepository.existsByEmail("jane.doe@example.com")).thenReturn(false);
        when(customerMapper.toEntity(request)).thenReturn(entity);
        when(customerRepository.save(entity)).thenReturn(saved);
        when(customerMapper.toResponse(saved)).thenReturn(response);

        customerService.createCustomer(request);

        assertThat(meterRegistry.get("customer.created.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("customer.creation.failed.total").counter().count()).isEqualTo(0.0);
    }

    @Test
    void createCustomer_incrementsFailedCounter_whenDuplicate() {
        CreateCustomerRequest request = new CreateCustomerRequest("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        when(customerRepository.existsByCustomerNumber("CUST-1")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(DuplicateCustomerException.class);

        assertThat(meterRegistry.get("customer.creation.failed.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("customer.created.total").counter().count()).isEqualTo(0.0);
    }

    @Test
    void createCustomer_recordsTimerData_regardlessOfOutcome() {
        CreateCustomerRequest request = new CreateCustomerRequest("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        when(customerRepository.existsByCustomerNumber("CUST-1")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(DuplicateCustomerException.class);

        assertThat(meterRegistry.get("customer.creation.duration").timer().count()).isEqualTo(1L);
    }

    @Test
    void getCustomerById_incrementsRetrievedCounter_whenFound() {
        Customer customer = new Customer("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        CustomerResponse response = new CustomerResponse(
                1L, "CUST-1", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerMapper.toResponse(customer)).thenReturn(response);

        customerService.getCustomerById(1L);

        assertThat(meterRegistry.get("customer.retrieved.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void getCustomerById_doesNotIncrementRetrievedCounter_whenMissing() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomerById(99L))
                .isInstanceOf(CustomerNotFoundException.class);

        assertThat(meterRegistry.get("customer.retrieved.total").counter().count()).isEqualTo(0.0);
    }
}
