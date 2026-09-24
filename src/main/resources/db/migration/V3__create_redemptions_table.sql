CREATE TABLE redemptions (
    id BIGSERIAL PRIMARY KEY,
    redemption_id VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL,
    vendor VARCHAR(100) NOT NULL,
    points BIGINT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_redemptions_redemption_id UNIQUE (redemption_id),
    CONSTRAINT fk_redemptions_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_redemptions_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')),
    CONSTRAINT ck_redemptions_points_positive CHECK (points > 0),
    CONSTRAINT ck_redemptions_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT ck_redemptions_currency_format CHECK (currency ~ '^[A-Z]{3}$')
);

-- PostgreSQL does not index foreign key columns automatically; without this, every customer
-- delete scans redemptions to enforce fk_redemptions_customer.
CREATE INDEX idx_redemptions_customer_id ON redemptions (customer_id);
