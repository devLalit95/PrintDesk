package com.example.backend.entity;

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
        name = "print_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_print_jobs_order_attempt",
                columnNames = {"print_order_id", "attempt_number"}),
        indexes = {
                @Index(name = "idx_print_jobs_status_queued", columnList = "status, queued_at"),
                @Index(name = "idx_print_jobs_printer_status", columnList = "printer_id, status"),
                @Index(name = "idx_print_jobs_agent_id", columnList = "agent_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintJobEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "print_order_id", nullable = false)
    private PrintOrderEntity printOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id")
    private PrinterEntity printer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private PrintAgentEntity agent;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PrintJobStatus status = PrintJobStatus.QUEUED;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    public PrintJobEntity(
            PrintOrderEntity printOrder,
            PrinterEntity printer,
            PrintAgentEntity agent,
            int attemptNumber,
            Instant queuedAt) {
        this.printOrder = printOrder;
        this.printer = printer;
        this.agent = agent;
        this.attemptNumber = attemptNumber;
        this.queuedAt = queuedAt;
    }

    public void claim(Instant claimedAt) {
        this.claimedAt = claimedAt;
        this.status = PrintJobStatus.CLAIMED;
    }

    public void start(Instant startedAt) {
        this.startedAt = startedAt;
        this.status = PrintJobStatus.PRINTING;
    }

    public void complete(PrintJobStatus finalStatus, String errorMessage, Instant completedAt) {
        complete(finalStatus, null, errorMessage, completedAt);
    }

    public void complete(
            PrintJobStatus finalStatus,
            String errorCode,
            String errorMessage,
            Instant completedAt) {
        this.status = finalStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = completedAt;
    }

    public void releaseClaim() {
        this.status = PrintJobStatus.QUEUED;
        this.claimedAt = null;
    }
}
