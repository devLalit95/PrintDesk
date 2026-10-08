package com.example.backend.dto.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentPrinterRegistration(
        @NotBlank(message = "A host system printer name is required.")
        @Size(max = 255, message = "The host system printer name is too long.")
        String systemName,
        @NotBlank(message = "A printer display name is required.")
        @Size(max = 128, message = "The printer display name is too long.")
        String displayName,
        @NotNull(message = "Printer capabilities are required.")
        @Valid
        AgentPrinterCapabilitiesRequest capabilities) {
}
