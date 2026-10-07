package com.example.backend.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.backend.entity.PrintOrderStatus;
import com.example.backend.entity.PrintType;

public record AdminPrintOrderDetail(
        UUID id,
        UUID documentId,
        String token,
        String fileName,
        String contentType,
        long sizeBytes,
        int pageCount,
        PrintType printType,
        int copies,
        int totalPages,
        String paperSize,
        String orientation,
        String pageRange,
        boolean doubleSided,
        BigDecimal pricePerPage,
        BigDecimal totalAmount,
        String currency,
        PrintOrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant printedAt,
        List<AdminPrintJobAttempt> attempts) {
}
