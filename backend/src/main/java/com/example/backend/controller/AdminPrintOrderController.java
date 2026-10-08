package com.example.backend.controller;

import java.util.UUID;

import com.example.backend.dto.admin.AdminDashboardStats;
import com.example.backend.dto.admin.AdminPrintOrderDetail;
import com.example.backend.dto.admin.AdminPrintOrderPage;
import com.example.backend.dto.admin.AdminPrintRequest;
import com.example.backend.dto.admin.AdminQueueResponse;
import com.example.backend.dto.admin.AdminUnknownOutcomeRequest;
import com.example.backend.entity.PrintOrderStatus;
import com.example.backend.service.admin.AdminPrintQueueService;
import com.example.backend.service.admin.AdminPrintOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminPrintOrderController {

    private final AdminPrintOrderService adminPrintOrderService;
    private final AdminPrintQueueService adminPrintQueueService;

    public AdminPrintOrderController(
            AdminPrintOrderService adminPrintOrderService,
            AdminPrintQueueService adminPrintQueueService) {
        this.adminPrintOrderService = adminPrintOrderService;
        this.adminPrintQueueService = adminPrintQueueService;
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<AdminDashboardStats> getStats() {
        return ResponseEntity.ok(adminPrintOrderService.getStats());
    }

    @GetMapping("/print-orders")
    public ResponseEntity<AdminPrintOrderPage> listOrders(
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) PrintOrderStatus status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(adminPrintOrderService.listOrders(page, size, status, search));
    }

    @GetMapping("/print-orders/{orderId}")
    public ResponseEntity<AdminPrintOrderDetail> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(adminPrintOrderService.getOrder(orderId));
    }

    @PostMapping("/print-orders/{orderId}/cancel")
    public ResponseEntity<AdminPrintOrderDetail> cancelOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(adminPrintOrderService.cancelOrder(
                orderId,
                UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/print-orders/{orderId}/print")
    public ResponseEntity<AdminQueueResponse> queueOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody AdminPrintRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.accepted().body(adminPrintQueueService.queueOrder(
                orderId, request, UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/print-jobs/{jobId}/retry")
    public ResponseEntity<AdminQueueResponse> retryFailedJob(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.accepted().body(adminPrintQueueService.retryFailedJob(
                jobId, UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/print-jobs/{jobId}/resolve-unknown")
    public ResponseEntity<AdminPrintOrderDetail> resolveUnknown(
            @PathVariable UUID jobId,
            @Valid @RequestBody AdminUnknownOutcomeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID orderId = adminPrintQueueService.resolveUnknown(
                jobId, request, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(adminPrintOrderService.getOrder(orderId));
    }
}
