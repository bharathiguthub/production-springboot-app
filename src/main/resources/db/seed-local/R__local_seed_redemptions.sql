-- LOCAL DEVELOPMENT SEED DATA ONLY.
-- This location is added to spring.flyway.locations by application-local.yml alone; the test and prod
-- profiles never load it. It is a repeatable migration so that it carries no version number that could
-- collide with future production migrations, and it re-runs whenever this file changes, so every statement
-- must stay idempotent.

-- Customer 1 owns the sample redemptions. An existing customer 1 is left untouched.
INSERT INTO customers (id, customer_number, first_name, last_name, email, created_at, updated_at)
VALUES (1, 'CUST-1001', 'Local', 'Seed', 'local.seed.customer@example.com',
        TIMESTAMP WITH TIME ZONE '2026-09-01 09:00:00+00', TIMESTAMP WITH TIME ZONE '2026-09-01 09:00:00+00')
ON CONFLICT DO NOTHING;

-- An explicit id does not advance the identity sequence; move it past every existing id so that
-- customers created later through the API cannot collide with the seeded row.
SELECT setval(pg_get_serial_sequence('customers', 'id'), GREATEST((SELECT MAX(id) FROM customers), 1));

-- A failed and a successful redemption with the same vendor and amount, for side-by-side comparison.
INSERT INTO redemptions (redemption_id, customer_id, vendor, points, amount, currency, status, error_code,
                         created_at, updated_at)
VALUES ('RDM-1001', 1, 'PAYPAL', 5000, 50.00, 'USD', 'FAILED', 'VENDOR_TIMEOUT',
        TIMESTAMP WITH TIME ZONE '2026-09-20 10:15:00+00', TIMESTAMP WITH TIME ZONE '2026-09-20 10:15:30+00'),
       ('RDM-1002', 1, 'PAYPAL', 5000, 50.00, 'USD', 'SUCCESS', NULL,
        TIMESTAMP WITH TIME ZONE '2026-09-20 11:00:00+00', TIMESTAMP WITH TIME ZONE '2026-09-20 11:00:05+00')
ON CONFLICT (redemption_id) DO UPDATE SET
    customer_id = EXCLUDED.customer_id,
    vendor = EXCLUDED.vendor,
    points = EXCLUDED.points,
    amount = EXCLUDED.amount,
    currency = EXCLUDED.currency,
    status = EXCLUDED.status,
    error_code = EXCLUDED.error_code,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;
