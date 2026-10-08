package com.example.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUnknownOutcomeRequest(
        @NotNull(message = "An outcome decision is required.")
        Decision decision,
        @NotBlank(message = "An audit note is required.")
        @Size(max = 1000, message = "The audit note must not exceed 1000 characters.")
        String notes) {

    public enum Decision {
        CONFIRMED_FAILED,
        CONFIRMED_PRINTED
    }
}
