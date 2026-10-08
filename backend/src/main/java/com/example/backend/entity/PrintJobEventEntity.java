package com.example.backend.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "print_job_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_print_job_events_job_event",
                columnNames = {"print_job_id", "event_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintJobEventEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "print_job_id", nullable = false)
    private PrintJobEntity job;

    @Column(name = "event_id", nullable = false, columnDefinition = "binary(16)")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PrintJobStatus status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public PrintJobEventEntity(
            PrintJobEntity job,
            UUID eventId,
            PrintJobStatus status,
            Instant occurredAt,
            String errorCode,
            String errorMessage) {
        this.job = job;
        this.eventId = eventId;
        this.status = status;
        this.occurredAt = occurredAt;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
