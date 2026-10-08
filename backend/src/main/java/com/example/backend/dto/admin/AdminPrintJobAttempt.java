package com.example.backend.dto.admin;

import java.time.Instant;
import java.util.UUID;

import com.example.backend.entity.PrintJobStatus;

public record AdminPrintJobAttempt(
        UUID id,
        int attemptNumber,
        PrintJobStatus status,
        String errorCode,
        String errorMessage,
        Instant queuedAt,
        Instant startedAt,
        Instant completedAt) {
}
