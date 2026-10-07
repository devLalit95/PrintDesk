package com.example.backend.dto.order;

import java.util.UUID;

import com.example.backend.entity.PrintType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PriceEstimateRequest(
        @NotNull(message = "A document is required.")
        UUID documentId,
        @NotNull(message = "A print type is required.")
        PrintType printType,
        @Positive(message = "Copies must be positive.")
        int copies) {
}
