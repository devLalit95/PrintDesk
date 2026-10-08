package com.example.backend.dto.agent;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentHeartbeatRequest(
        @NotNull(message = "The agent observation time is required.")
        Instant observedAt,
        @NotNull(message = "The discovered printer list is required.")
        @Size(max = 64, message = "An agent may report at most 64 printers at a time.")
        List<@Valid AgentPrinterRegistration> printers) {
}
