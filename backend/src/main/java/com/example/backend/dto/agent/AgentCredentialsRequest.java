package com.example.backend.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AgentCredentialsRequest(
        @NotBlank(message = "An agent code is required.")
        @Size(max = 64, message = "The agent code must not exceed 64 characters.")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "The agent code contains unsupported characters.")
        String agentCode,
        @NotBlank(message = "An agent secret is required.")
        @Size(min = 32, max = 72, message = "The agent secret must be between 32 and 72 characters.")
        String secret) {

    @Override
    public String toString() {
        return "AgentCredentialsRequest[agentCode=" + agentCode + ", secret=[REDACTED]]";
    }
}
