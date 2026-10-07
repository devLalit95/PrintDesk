package com.example.backend.service.pricing;

import java.math.BigDecimal;

import com.example.backend.entity.PrintType;

public record PriceQuote(
        PrintType printType,
        int documentPages,
        int copies,
        int totalPages,
        BigDecimal pricePerPage,
        BigDecimal totalAmount,
        String currency) {
}
