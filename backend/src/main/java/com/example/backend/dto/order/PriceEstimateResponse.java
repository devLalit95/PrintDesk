package com.example.backend.dto.order;

import java.math.BigDecimal;

import com.example.backend.entity.PrintType;

public record PriceEstimateResponse(
        PrintType printType,
        int documentPages,
        int copies,
        int totalPages,
        BigDecimal pricePerPage,
        BigDecimal totalAmount,
        String currency) {
}
