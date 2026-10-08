package com.example.backend.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentCredentialRotationRequest(
        @NotBlank(message = "An agent secret is required.")
        @Size(min = 32, max = 72, message = "The agent secret must be between 32 and 72 characters.")
        String secret) {

    @Override
    public String toString() {
        return "AgentCredentialRotationRequest[secret=[REDACTED]]";
    }
}
