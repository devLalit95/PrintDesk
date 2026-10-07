package com.example.backend.dto.order;

import java.math.BigDecimal;

import com.example.backend.entity.PrintOrderStatus;
import com.example.backend.entity.PrintType;

public record CreatePrintOrderResponse(
        String token,
        String fileName,
        int pageCount,
        PrintType printType,
        int copies,
        int totalPages,
        BigDecimal pricePerPage,
        BigDecimal totalAmount,
        String currency,
        PrintOrderStatus status) {
}
