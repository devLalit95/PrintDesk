package com.example.backend.dto.admin;

public record AdminDashboardStats(
        long totalOrders,
        long pendingOrders,
        long inProgressOrders,
        long printedOrders,
        long failedOrders,
        long cancelledOrders) {
}
