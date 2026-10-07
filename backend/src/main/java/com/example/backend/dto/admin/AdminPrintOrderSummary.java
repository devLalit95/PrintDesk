package com.example.backend.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.backend.entity.PrintOrderStatus;
import com.example.backend.entity.PrintType;

public record AdminPrintOrderSummary(
        UUID id,
        String token,
        String fileName,
        int pageCount,
        PrintType printType,
        int copies,
        int totalPages,
        String paperSize,
        String orientation,
        boolean doubleSided,
        BigDecimal totalAmount,
        String currency,
        PrintOrderStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
