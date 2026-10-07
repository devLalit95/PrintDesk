package com.example.backend.dto.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.backend.entity.PrintOrderStatus;
import com.example.backend.entity.PrintType;

public record PrintOrderStatusResponse(
        String token,
        PrintOrderStatus status,
        PrintType printType,
        int pageCount,
        int copies,
        int totalPages,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        Instant printedAt) {
}
