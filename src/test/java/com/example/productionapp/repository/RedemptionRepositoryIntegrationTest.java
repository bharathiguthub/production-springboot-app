package com.example.productionapp.repository;

import com.example.productionapp.AbstractIntegrationTest;
import com.example.productionapp.entity.Customer;
import com.example.productionapp.entity.Redemption;
import com.example.productionapp.entity.RedemptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RedemptionRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RedemptionRepository redemptionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long customerId;

    @BeforeEach
    void createCustomer() {
        customerId = customerRepository.saveAndFlush(
                new Customer("CUST-R1", "Rita", "Stone", "rita.stone@example.com")).getId();
    }

    @Test
    void savesAndFindsRedemptionByRedemptionId() {
        redemptionRepository.saveAndFlush(redemption("RDM-T1", customerId, RedemptionStatus.FAILED, "VENDOR_TIMEOUT"));
        entityManager.clear();

        Redemption found = redemptionRepository.findByRedemptionId("RDM-T1").orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getCustomerId()).isEqualTo(customerId);
        assertThat(found.getVendor()).isEqualTo("PAYPAL");
        assertThat(found.getPoints()).isEqualTo(5000L);
        assertThat(found.getAmount()).isEqualTo(new BigDecimal("50.00"));
        assertThat(found.getCurrency()).isEqualTo("USD");
        assertThat(found.getStatus()).isEqualTo(RedemptionStatus.FAILED);
        assertThat(found.getErrorCode()).isEqualTo("VENDOR_TIMEOUT");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByRedemptionId_returnsEmpty_whenRedemptionIdUnknown() {
        assertThat(redemptionRepository.findByRedemptionId("RDM-UNKNOWN")).isEmpty();
    }

    @Test
    void persistsNullErrorCode() {
        redemptionRepository.saveAndFlush(redemption("RDM-T2", customerId, RedemptionStatus.SUCCESS, null));
        entityManager.clear();

        assertThat(redemptionRepository.findByRedemptionId("RDM-T2").orElseThrow().getErrorCode()).isNull();
    }

    @Test
    void storesStatusAsTextAndAmountWithTwoDecimalScale() {
        redemptionRepository.saveAndFlush(new Redemption(
                "RDM-T3", customerId, "PAYPAL", 100L, new BigDecimal("7.5"), "USD", RedemptionStatus.PENDING, null));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, amount::text AS amount, currency FROM redemptions WHERE redemption_id = 'RDM-T3'");

        assertThat(row).containsEntry("status", "PENDING")
                .containsEntry("amount", "7.50")
                .containsEntry("currency", "USD");
    }

    @Test
    void save_throwsDataIntegrityViolation_whenRedemptionIdDuplicated() {
        redemptionRepository.saveAndFlush(redemption("RDM-T4", customerId, RedemptionStatus.SUCCESS, null));
        Redemption duplicate = redemption("RDM-T4", customerId, RedemptionStatus.FAILED, "VENDOR_TIMEOUT");

        assertThatThrownBy(() -> redemptionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_throwsDataIntegrityViolation_whenCustomerDoesNotExist() {
        Redemption orphan = redemption("RDM-T5", Long.MAX_VALUE, RedemptionStatus.SUCCESS, null);

        assertThatThrownBy(() -> redemptionRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_throwsDataIntegrityViolation_whenCurrencyIsNotThreeUppercaseLetters() {
        Redemption invalidCurrency = new Redemption(
                "RDM-T6", customerId, "PAYPAL", 100L, new BigDecimal("1.00"), "us$", RedemptionStatus.SUCCESS, null);

        assertThatThrownBy(() -> redemptionRepository.saveAndFlush(invalidCurrency))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_throwsDataIntegrityViolation_whenPointsNotPositive() {
        Redemption zeroPoints = new Redemption(
                "RDM-T7", customerId, "PAYPAL", 0L, new BigDecimal("1.00"), "USD", RedemptionStatus.SUCCESS, null);

        assertThatThrownBy(() -> redemptionRepository.saveAndFlush(zeroPoints))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingCustomerWithRedemptions_isRejectedByForeignKey() {
        redemptionRepository.saveAndFlush(redemption("RDM-T8", customerId, RedemptionStatus.SUCCESS, null));

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM customers WHERE id = ?", customerId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Redemption redemption(String redemptionId, Long customerId, RedemptionStatus status, String errorCode) {
        return new Redemption(redemptionId, customerId, "PAYPAL", 5000L, new BigDecimal("50.00"), "USD", status, errorCode);
    }
}
