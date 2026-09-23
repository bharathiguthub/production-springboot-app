package com.example.productionapp.service;

import com.example.productionapp.dto.CreateCustomerRequest;
import com.example.productionapp.dto.CustomerResponse;
import com.example.productionapp.dto.UpdateCustomerRequest;
import com.example.productionapp.entity.Customer;
import com.example.productionapp.exception.CustomerNotFoundException;
import com.example.productionapp.exception.DuplicateCustomerException;
import com.example.productionapp.mapper.CustomerMapper;
import com.example.productionapp.repository.CustomerRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final Counter customerCreatedCounter;
    private final Counter customerRetrievedCounter;
    private final Counter customerCreationFailedCounter;
    private final Timer customerCreationTimer;

    public CustomerService(CustomerRepository customerRepository, CustomerMapper customerMapper, MeterRegistry meterRegistry) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.customerCreatedCounter = Counter.builder("customer.created.total")
                .description("Number of customers successfully created")
                .register(meterRegistry);
        this.customerRetrievedCounter = Counter.builder("customer.retrieved.total")
                .description("Number of customers successfully retrieved by id")
                .register(meterRegistry);
        this.customerCreationFailedCounter = Counter.builder("customer.creation.failed.total")
                .description("Number of failed customer creation attempts")
                .register(meterRegistry);
        this.customerCreationTimer = Timer.builder("customer.creation.duration")
                .description("Time taken to process a customer creation request")
                .register(meterRegistry);
    }

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        Timer.Sample sample = Timer.start();
        try {
            log.info("Creating customer customerNumber={}", request.customerNumber());

            if (customerRepository.existsByCustomerNumber(request.customerNumber())) {
                throw new DuplicateCustomerException("Customer number already exists: " + request.customerNumber());
            }
            if (customerRepository.existsByEmail(request.email())) {
                throw new DuplicateCustomerException("Email already exists");
            }

            Customer customer = customerMapper.toEntity(request);
            Customer saved = customerRepository.save(customer);

            customerCreatedCounter.increment();
            log.info("Customer created customerId={}", saved.getId());
            return customerMapper.toResponse(saved);
        } catch (RuntimeException ex) {
            customerCreationFailedCounter.increment();
            throw ex;
        } finally {
            sample.stop(customerCreationTimer);
        }
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id: " + id));
        customerRetrievedCounter.increment();
        return customerMapper.toResponse(customer);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAllCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable)
                .map(customerMapper::toResponse);
    }

    @Transactional
    public CustomerResponse updateCustomer(Long id, UpdateCustomerRequest request) {
        log.info("Updating customer customerId={}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id: " + id));

        if (customerRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new DuplicateCustomerException("Email already exists");
        }

        customer.updateContactDetails(request.firstName(), request.lastName(), request.email());

        log.info("Customer updated customerId={}", customer.getId());
        return customerMapper.toResponse(customer);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        log.info("Deleting customer customerId={}", id);

        if (!customerRepository.existsById(id)) {
            throw new CustomerNotFoundException("Customer not found with id: " + id);
        }
        customerRepository.deleteById(id);

        log.info("Customer deleted customerId={}", id);
    }
}
