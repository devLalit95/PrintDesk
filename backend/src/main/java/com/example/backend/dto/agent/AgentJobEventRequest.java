package com.example.backend.dto.agent;

import java.time.Instant;
import java.util.UUID;

import com.example.backend.entity.PrintJobStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AgentJobEventRequest(
        @NotNull(message = "An event identifier is required.")
        UUID eventId,
        @NotNull(message = "A job status is required.")
        PrintJobStatus status,
        @NotNull(message = "The event time is required.")
        Instant occurredAt,
        @Pattern(regexp = "[A-Za-z0-9_-]*", message = "The error code contains unsupported characters.")
        @Size(max = 64, message = "The error code must not exceed 64 characters.")
        String errorCode,
        @Size(max = 1000, message = "The error message must not exceed 1000 characters.")
        String errorMessage) {
}
