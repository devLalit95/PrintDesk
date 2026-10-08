package com.example.backend.dto.agent;

import java.time.Instant;
import java.util.UUID;

import com.example.backend.entity.PrintJobStatus;

public record AgentJobEventResponse(
        UUID jobId,
        PrintJobStatus status,
        boolean accepted,
        boolean duplicate,
        Instant updatedAt) {
}
