package com.example.backend.dto.agent;

import java.time.Instant;
import java.util.UUID;

import com.example.backend.entity.PrintAgentStatus;

public record AdminAgentResponse(
        UUID agentId,
        String agentCode,
        PrintAgentStatus status,
        Instant createdAt) {
}
