package com.example.productionapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "redemptions")
public class Redemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "redemption_id", nullable = false, unique = true, length = 64)
    private String redemptionId;

    // Plain foreign key value rather than a @ManyToOne: redemption lookups never need the customer
    // entity, and the database constraint fk_redemptions_customer guarantees it exists.
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "vendor", nullable = false, length = 100)
    private String vendor;

    @Column(name = "points", nullable = false)
    private Long points;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // The column is CHAR(3); without this Hibernate expects VARCHAR and schema validation fails.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RedemptionStatus status;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Redemption() {
        // required by JPA
    }

    public Redemption(String redemptionId, Long customerId, String vendor, Long points, BigDecimal amount,
                      String currency, RedemptionStatus status, String errorCode) {
        this.redemptionId = redemptionId;
        this.customerId = customerId;
        this.vendor = vendor;
        this.points = points;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.errorCode = errorCode;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getRedemptionId() {
        return redemptionId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getVendor() {
        return vendor;
    }

    public Long getPoints() {
        return points;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public RedemptionStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
