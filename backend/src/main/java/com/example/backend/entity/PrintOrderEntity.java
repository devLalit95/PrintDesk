package com.example.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "print_orders",
        uniqueConstraints = @UniqueConstraint(name = "uq_print_orders_token", columnNames = "token"),
        indexes = {
                @Index(name = "idx_print_orders_status_created", columnList = "status, created_at"),
                @Index(name = "idx_print_orders_document_id", columnList = "document_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintOrderEntity extends BaseEntity {

    @Column(name = "token", nullable = false, length = 32)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "print_type", nullable = false, length = 32)
    private PrintType printType;

    @Column(name = "copies", nullable = false)
    private int copies;

    @Column(name = "price_per_page", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerPage;

    @Column(name = "total_pages", nullable = false)
    private int totalPages;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "paper_size", nullable = false, length = 32)
    private String paperSize;

    @Column(name = "orientation", nullable = false, length = 16)
    private String orientation;

    @Column(name = "page_range", length = 128)
    private String pageRange;

    @Column(name = "double_sided", nullable = false)
    private boolean doubleSided;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PrintOrderStatus status = PrintOrderStatus.PENDING;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "printed_at")
    private Instant printedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    public PrintOrderEntity(
            String token,
            DocumentEntity document,
            int pageCount,
            PrintType printType,
            int copies,
            BigDecimal pricePerPage,
            int totalPages,
            BigDecimal totalAmount,
            String paperSize,
            String orientation,
            String pageRange,
            boolean doubleSided) {
        this.token = token;
        this.document = document;
        this.pageCount = pageCount;
        this.printType = printType;
        this.copies = copies;
        this.pricePerPage = pricePerPage;
        this.totalPages = totalPages;
        this.totalAmount = totalAmount;
        this.paperSize = paperSize;
        this.orientation = orientation;
        this.pageRange = pageRange;
        this.doubleSided = doubleSided;
    }

    public void transitionTo(PrintOrderStatus nextStatus, Instant transitionedAt) {
        if (nextStatus == null || !canTransitionTo(nextStatus)) {
            throw new InvalidOrderTransitionException(status, nextStatus);
        }
        if (nextStatus == PrintOrderStatus.PRINTED && transitionedAt == null) {
            throw new InvalidOrderTransitionException(status, nextStatus);
        }

        this.status = nextStatus;
        if (nextStatus == PrintOrderStatus.PRINTED) {
            this.printedAt = transitionedAt;
        }
    }

    private boolean canTransitionTo(PrintOrderStatus nextStatus) {
        return switch (status) {
            case PENDING -> nextStatus == PrintOrderStatus.PRINT_REQUESTED
                    || nextStatus == PrintOrderStatus.CANCELLED;
            case PRINT_REQUESTED -> nextStatus == PrintOrderStatus.QUEUED
                    || nextStatus == PrintOrderStatus.FAILED
                    || nextStatus == PrintOrderStatus.CANCELLED;
            case QUEUED -> nextStatus == PrintOrderStatus.PRINTING
                    || nextStatus == PrintOrderStatus.FAILED
                    || nextStatus == PrintOrderStatus.CANCELLED;
            case PRINTING -> nextStatus == PrintOrderStatus.PRINTED
                    || nextStatus == PrintOrderStatus.FAILED
                    || nextStatus == PrintOrderStatus.OUTCOME_UNKNOWN;
            case FAILED -> nextStatus == PrintOrderStatus.PRINT_REQUESTED;
            case OUTCOME_UNKNOWN -> nextStatus == PrintOrderStatus.PRINTED
                    || nextStatus == PrintOrderStatus.FAILED;
            case PRINTED, CANCELLED -> false;
        };
    }
}
