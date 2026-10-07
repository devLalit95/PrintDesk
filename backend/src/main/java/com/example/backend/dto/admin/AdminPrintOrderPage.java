package com.example.backend.dto.admin;

import java.util.List;

public record AdminPrintOrderPage(
        List<AdminPrintOrderSummary> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean first,
        boolean last) {
}
