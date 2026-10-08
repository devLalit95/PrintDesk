package com.example.backend.dto.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentJobAvailableNotification(
        UUID eventId,
        String eventType,
        UUID jobId,
        UUID printerId,
        Instant occurredAt) {
}
