package com.example.productionapp;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the local-profile seed script exactly as the {@code local} profile configures Flyway, but in a
 * throwaway schema of the shared test container so that the seed rows never reach other tests' data.
 * No Spring context is needed.
 */
class LocalSeedMigrationIntegrationTest extends AbstractIntegrationTest {

    private static final String SCHEMA = "local_seed_check";

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void dropSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void seedCreatesFailedAndSuccessfulSampleRedemptionsForCustomer1() {
        flyway().migrate();

        Map<String, Object> failed = redemption("RDM-1001");
        assertThat(failed).containsEntry("customer_id", 1L)
                .containsEntry("vendor", "PAYPAL")
                .containsEntry("points", 5000L)
                .containsEntry("amount", new BigDecimal("50.00"))
                .containsEntry("currency", "USD")
                .containsEntry("status", "FAILED")
                .containsEntry("error_code", "VENDOR_TIMEOUT");

        Map<String, Object> successful = redemption("RDM-1002");
        assertThat(successful).containsEntry("customer_id", 1L)
                .containsEntry("vendor", "PAYPAL")
                .containsEntry("amount", new BigDecimal("50.00"))
                .containsEntry("status", "SUCCESS")
                .containsEntry("error_code", null);

        assertThat(count("customers WHERE id = 1")).isEqualTo(1);
    }

    @Test
    void seedAdvancesCustomerSequencePastSeededId() {
        flyway().migrate();

        Long nextId = jdbcTemplate.queryForObject(
                "INSERT INTO " + SCHEMA + ".customers (customer_number, first_name, last_name, email, created_at, updated_at) "
                        + "VALUES ('CUST-NEXT', 'Next', 'Customer', 'next@example.com', now(), now()) RETURNING id",
                Long.class);

        assertThat(nextId).isGreaterThan(1L);
    }

    @Test
    void seedIsIdempotentWhenRunAgain() {
        flyway().migrate();
        // Forces Flyway to re-apply the repeatable seed, as happens locally whenever the file changes.
        jdbcTemplate.update("UPDATE " + SCHEMA + ".flyway_schema_history SET checksum = 0 WHERE version IS NULL");

        flyway().migrate();

        assertThat(count("redemptions")).isEqualTo(2);
        assertThat(count("customers")).isEqualTo(1);
        assertThat(count("flyway_schema_history WHERE script = 'R__local_seed_redemptions.sql' AND success"))
                .isEqualTo(2);
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration", "classpath:db/seed-local")
                .load();
    }

    private Map<String, Object> redemption(String redemptionId) {
        return jdbcTemplate.queryForMap(
                "SELECT customer_id, vendor, points, amount, currency, status, error_code FROM " + SCHEMA
                        + ".redemptions WHERE redemption_id = ?", redemptionId);
    }

    private int count(String fromClause) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + SCHEMA + "." + fromClause, Integer.class);
    }
}
