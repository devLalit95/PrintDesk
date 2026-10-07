package com.example.backend.dto.order;

import java.util.UUID;

import com.example.backend.entity.PrintType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreatePrintOrderRequest(
        @NotNull(message = "A document is required.")
        UUID documentId,
        @NotNull(message = "A print type is required.")
        PrintType printType,
        @Positive(message = "Copies must be positive.")
        int copies,
        @NotBlank(message = "A paper size is required.")
        @Pattern(regexp = "(?i)(A4|A3|Letter|Legal)", message = "Choose a supported paper size.")
        String paperSize,
        @NotBlank(message = "An orientation is required.")
        @Pattern(regexp = "(?i)(portrait|landscape)", message = "Choose a supported orientation.")
        String orientation,
        boolean doubleSided) {
}
