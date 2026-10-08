package com.example.backend.dto.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentTokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UUID agentId,
        String agentCode) {
}
