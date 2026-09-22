package com.example.productionapp.controller;

import com.example.productionapp.dto.CreateCustomerRequest;
import com.example.productionapp.dto.CustomerResponse;
import com.example.productionapp.dto.UpdateCustomerRequest;
import com.example.productionapp.exception.CustomerNotFoundException;
import com.example.productionapp.exception.DuplicateCustomerException;
import com.example.productionapp.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void createCustomer_returns201_whenValid() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        CustomerResponse response = new CustomerResponse(
                1L, "CUST-1", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        when(customerService.createCustomer(any(CreateCustomerRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/customers/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customerNumber").value("CUST-1"));
    }

    @Test
    void createCustomer_returns400_whenInvalid() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest("", "", "", "not-an-email");

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCustomer_returns409_whenDuplicate() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest("CUST-1", "Jane", "Doe", "jane.doe@example.com");
        when(customerService.createCustomer(any(CreateCustomerRequest.class)))
                .thenThrow(new DuplicateCustomerException("Customer number already exists: CUST-1"));

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void getCustomerById_returns200_whenFound() throws Exception {
        CustomerResponse response = new CustomerResponse(
                1L, "CUST-1", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        when(customerService.getCustomerById(eq(1L))).thenReturn(response);

        mockMvc.perform(get("/api/v1/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getCustomerById_returns404_whenMissing() throws Exception {
        when(customerService.getCustomerById(eq(99L)))
                .thenThrow(new CustomerNotFoundException("Customer not found with id: 99"));

        mockMvc.perform(get("/api/v1/customers/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllCustomers_returns200_withPageResponse() throws Exception {
        CustomerResponse response = new CustomerResponse(
                1L, "CUST-1", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        when(customerService.getAllCustomers(any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(response), invocation.getArgument(0), 1));

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void getAllCustomers_appliesConfiguredPageSizeLimit_whenRequestedSizeExceedsMax() throws Exception {
        CustomerResponse response = new CustomerResponse(
                1L, "CUST-1", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(customerService.getAllCustomers(pageableCaptor.capture()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(response), invocation.getArgument(0), 1));

        mockMvc.perform(get("/api/v1/customers").param("page", "0").param("size", "5000"))
                .andExpect(status().isOk());

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void getAllCustomers_honorsSortParameter() throws Exception {
        CustomerResponse response = new CustomerResponse(
                1L, "CUST-1", "Jane", "Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(customerService.getAllCustomers(pageableCaptor.capture()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(response), invocation.getArgument(0), 1));

        mockMvc.perform(get("/api/v1/customers").param("sort", "createdAt,desc"))
                .andExpect(status().isOk());

        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void updateCustomer_returns200_whenValid() throws Exception {
        UpdateCustomerRequest request = new UpdateCustomerRequest("Jane", "Smith", "jane.smith@example.com");
        CustomerResponse response = new CustomerResponse(
                1L, "CUST-1", "Jane", "Smith", "jane.smith@example.com", Instant.now(), Instant.now());
        when(customerService.updateCustomer(eq(1L), any(UpdateCustomerRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.email").value("jane.smith@example.com"));
    }

    @Test
    void updateCustomer_returns404_whenMissing() throws Exception {
        UpdateCustomerRequest request = new UpdateCustomerRequest("Jane", "Smith", "jane.smith@example.com");
        when(customerService.updateCustomer(eq(99L), any(UpdateCustomerRequest.class)))
                .thenThrow(new CustomerNotFoundException("Customer not found with id: 99"));

        mockMvc.perform(put("/api/v1/customers/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCustomer_returns409_whenEmailBelongsToAnotherCustomer() throws Exception {
        UpdateCustomerRequest request = new UpdateCustomerRequest("Jane", "Smith", "taken@example.com");
        when(customerService.updateCustomer(eq(1L), any(UpdateCustomerRequest.class)))
                .thenThrow(new DuplicateCustomerException("Email already exists"));

        mockMvc.perform(put("/api/v1/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateCustomer_returns400_whenInvalid() throws Exception {
        UpdateCustomerRequest request = new UpdateCustomerRequest("", "", "not-an-email");

        mockMvc.perform(put("/api/v1/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteCustomer_returns204_whenExists() throws Exception {
        mockMvc.perform(delete("/api/v1/customers/1"))
                .andExpect(status().isNoContent());

        verify(customerService).deleteCustomer(1L);
    }

    @Test
    void deleteCustomer_returns404_whenMissing() throws Exception {
        doThrow(new CustomerNotFoundException("Customer not found with id: 99"))
                .when(customerService).deleteCustomer(anyLong());

        mockMvc.perform(delete("/api/v1/customers/99"))
                .andExpect(status().isNotFound());
    }
}
