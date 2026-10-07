package com.example.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "print_rates",
        uniqueConstraints = @UniqueConstraint(name = "uq_print_rates_type", columnNames = "print_type"),
        indexes = @Index(name = "idx_print_rates_active", columnList = "active"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintRateEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "print_type", nullable = false, length = 32)
    private PrintType printType;

    @Column(name = "price_per_page", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerPage;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PrintRateEntity(PrintType printType, BigDecimal pricePerPage, String currency) {
        this.printType = printType;
        this.pricePerPage = pricePerPage;
        this.currency = currency;
    }

    public void updateRate(BigDecimal pricePerPage, String currency, boolean active) {
        this.pricePerPage = pricePerPage;
        this.currency = currency;
        this.active = active;
    }
}
