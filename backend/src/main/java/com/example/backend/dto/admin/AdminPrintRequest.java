package com.example.backend.dto.admin;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AdminPrintRequest(@NotNull(message = "A printer identifier is required.") UUID printerId) {
}
