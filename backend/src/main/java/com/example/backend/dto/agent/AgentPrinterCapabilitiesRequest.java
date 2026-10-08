package com.example.backend.dto.agent;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentPrinterCapabilitiesRequest(
        boolean color,
        boolean duplex,
        @Min(value = 1, message = "Maximum copies must be positive.")
        @Max(value = 100, message = "Maximum copies must not exceed 100.")
        int maxCopies,
        @NotNull(message = "Paper sizes are required.")
        @Size(min = 1, max = 4, message = "Provide between one and four supported paper sizes.")
        List<@NotNull String> paperSizes) {
}
