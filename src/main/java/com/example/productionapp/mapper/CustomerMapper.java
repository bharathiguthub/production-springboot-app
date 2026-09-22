package com.example.productionapp.mapper;

import com.example.productionapp.dto.CreateCustomerRequest;
import com.example.productionapp.dto.CustomerResponse;
import com.example.productionapp.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public Customer toEntity(CreateCustomerRequest request) {
        return new Customer(
                request.customerNumber(),
                request.firstName(),
                request.lastName(),
                request.email()
        );
    }

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerNumber(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
