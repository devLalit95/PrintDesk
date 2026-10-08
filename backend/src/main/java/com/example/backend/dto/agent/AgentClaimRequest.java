package com.example.backend.dto.agent;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AgentClaimRequest(@NotNull(message = "A printer identifier is required.") UUID printerId) {
}
