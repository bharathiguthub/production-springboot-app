package com.example.productionapp.repository;

import com.example.productionapp.AbstractIntegrationTest;
import com.example.productionapp.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CustomerRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void savesAndFindsCustomer() {
        Customer customer = new Customer("CUST-1", "Jane", "Doe", "jane.doe@example.com");

        Customer saved = customerRepository.save(customer);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(customerRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void existsByCustomerNumber_returnsExpectedResult() {
        customerRepository.save(new Customer("CUST-2", "John", "Smith", "john.smith@example.com"));

        assertThat(customerRepository.existsByCustomerNumber("CUST-2")).isTrue();
        assertThat(customerRepository.existsByCustomerNumber("UNKNOWN")).isFalse();
    }

    @Test
    void existsByEmail_returnsExpectedResult() {
        customerRepository.save(new Customer("CUST-3", "Alice", "Brown", "alice.brown@example.com"));

        assertThat(customerRepository.existsByEmail("alice.brown@example.com")).isTrue();
        assertThat(customerRepository.existsByEmail("unknown@example.com")).isFalse();
    }

    @Test
    void save_throwsDataIntegrityViolation_whenCustomerNumberDuplicated() {
        customerRepository.saveAndFlush(new Customer("CUST-4", "Bob", "White", "bob.white@example.com"));
        Customer duplicate = new Customer("CUST-4", "Other", "Person", "other.person@example.com");

        assertThatThrownBy(() -> customerRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_throwsDataIntegrityViolation_whenEmailDuplicated() {
        customerRepository.saveAndFlush(new Customer("CUST-5", "Carl", "Young", "carl.young@example.com"));
        Customer duplicate = new Customer("CUST-6", "Carl", "Duplicate", "carl.young@example.com");

        assertThatThrownBy(() -> customerRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByEmailAndIdNot_returnsFalse_whenEmailBelongsToSameCustomer() {
        Customer saved = customerRepository.save(new Customer("CUST-7", "Dana", "Reed", "dana.reed@example.com"));

        assertThat(customerRepository.existsByEmailAndIdNot("dana.reed@example.com", saved.getId())).isFalse();
    }

    @Test
    void existsByEmailAndIdNot_returnsTrue_whenEmailBelongsToDifferentCustomer() {
        Customer other = customerRepository.save(new Customer("CUST-8", "Eli", "Stone", "eli.stone@example.com"));
        Customer saved = customerRepository.save(new Customer("CUST-9", "Fay", "Gray", "fay.gray@example.com"));

        assertThat(customerRepository.existsByEmailAndIdNot("eli.stone@example.com", saved.getId())).isTrue();
        assertThat(customerRepository.existsByEmailAndIdNot("unknown@example.com", other.getId())).isFalse();
    }

    @Test
    void findAll_returnsPagedAndSortedResults() {
        customerRepository.save(new Customer("CUST-10", "Gia", "Ho", "gia.ho@example.com"));
        customerRepository.save(new Customer("CUST-11", "Hal", "Ivy", "hal.ivy@example.com"));
        customerRepository.save(new Customer("CUST-12", "Ian", "Joy", "ian.joy@example.com"));

        Page<Customer> page = customerRepository.findAll(
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent().get(0).getCreatedAt())
                .isAfterOrEqualTo(page.getContent().get(1).getCreatedAt());
    }
}
