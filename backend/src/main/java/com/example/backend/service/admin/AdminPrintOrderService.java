package com.example.backend.service.admin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.example.backend.dto.admin.AdminDashboardStats;
import com.example.backend.dto.admin.AdminPrintJobAttempt;
import com.example.backend.dto.admin.AdminPrintOrderDetail;
import com.example.backend.dto.admin.AdminPrintOrderPage;
import com.example.backend.dto.admin.AdminPrintOrderSummary;
import com.example.backend.entity.AuditActorType;
import com.example.backend.entity.AuditLogEntity;
import com.example.backend.entity.InvalidOrderTransitionException;
import com.example.backend.entity.PrintJobEntity;
import com.example.backend.entity.PrintOrderEntity;
import com.example.backend.entity.PrintOrderStatus;
import com.example.backend.repository.AuditLogRepository;
import com.example.backend.repository.PrintJobRepository;
import com.example.backend.repository.PrintOrderRepository;
import com.example.backend.service.order.PrintOrderNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPrintOrderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final PrintOrderRepository orderRepository;
    private final PrintJobRepository jobRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminPrintOrderService(
            PrintOrderRepository orderRepository,
            PrintJobRepository jobRepository,
            AuditLogRepository auditLogRepository) {
        this.orderRepository = orderRepository;
        this.jobRepository = jobRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public AdminPrintOrderPage listOrders(
            int page,
            int size,
            PrintOrderStatus status,
            String search) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidAdminOrderQueryException("Choose a valid page and a page size between 1 and 100.");
        }
        String normalizedSearch = normalizeSearch(search);
        Page<PrintOrderEntity> result = orderRepository.searchForAdmin(
                status,
                normalizedSearch,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new AdminPrintOrderPage(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast());
    }

    @Transactional(readOnly = true)
    public AdminPrintOrderDetail getOrder(UUID orderId) {
        PrintOrderEntity order = findOrder(orderId);
        List<AdminPrintJobAttempt> attempts = jobRepository
                .findAllByPrintOrder_IdOrderByAttemptNumberDesc(orderId)
                .stream()
                .map(this::toAttempt)
                .toList();
        return toDetail(order, attempts);
    }

    @Transactional(readOnly = true)
    public AdminDashboardStats getStats() {
        long pending = orderRepository.countByStatus(PrintOrderStatus.PENDING);
        long printRequested = orderRepository.countByStatus(PrintOrderStatus.PRINT_REQUESTED);
        long queued = orderRepository.countByStatus(PrintOrderStatus.QUEUED);
        long printing = orderRepository.countByStatus(PrintOrderStatus.PRINTING);
        return new AdminDashboardStats(
                orderRepository.count(),
                pending,
                printRequested + queued + printing,
                orderRepository.countByStatus(PrintOrderStatus.PRINTED),
                orderRepository.countByStatus(PrintOrderStatus.FAILED),
                orderRepository.countByStatus(PrintOrderStatus.OUTCOME_UNKNOWN),
                orderRepository.countByStatus(PrintOrderStatus.CANCELLED));
    }

    @Transactional
    public AdminPrintOrderDetail cancelOrder(UUID orderId, UUID actorId) {
        PrintOrderEntity order = findOrder(orderId);
        PrintOrderStatus originalStatus = order.getStatus();
        if (originalStatus != PrintOrderStatus.PENDING) {
            throw new InvalidOrderTransitionException(originalStatus, PrintOrderStatus.CANCELLED);
        }
        order.transitionTo(PrintOrderStatus.CANCELLED, java.time.Instant.now());
        orderRepository.saveAndFlush(order);
        auditLogRepository.save(new AuditLogEntity(
                AuditActorType.ADMIN,
                actorId,
                "ORDER_CANCELLED",
                "PRINT_ORDER",
                order.getId().toString(),
                "from=" + originalStatus + ";to=" + PrintOrderStatus.CANCELLED));
        return toDetail(
                order,
                jobRepository.findAllByPrintOrder_IdOrderByAttemptNumberDesc(orderId)
                        .stream()
                        .map(this::toAttempt)
                        .toList());
    }

    private PrintOrderEntity findOrder(UUID orderId) {
        if (orderId == null) {
            throw new PrintOrderNotFoundException();
        }
        return orderRepository.findWithDocumentById(orderId)
                .orElseThrow(PrintOrderNotFoundException::new);
    }

    private AdminPrintOrderSummary toSummary(PrintOrderEntity order) {
        return new AdminPrintOrderSummary(
                order.getId(),
                order.getToken(),
                order.getDocument().getOriginalFileName(),
                order.getPageCount(),
                order.getPrintType(),
                order.getCopies(),
                order.getTotalPages(),
                order.getPaperSize(),
                order.getOrientation(),
                order.isDoubleSided(),
                order.getTotalAmount(),
                "INR",
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private AdminPrintOrderDetail toDetail(PrintOrderEntity order, List<AdminPrintJobAttempt> attempts) {
        return new AdminPrintOrderDetail(
                order.getId(),
                order.getDocument().getId(),
                order.getToken(),
                order.getDocument().getOriginalFileName(),
                order.getDocument().getContentType(),
                order.getDocument().getSizeBytes(),
                order.getPageCount(),
                order.getPrintType(),
                order.getCopies(),
                order.getTotalPages(),
                order.getPaperSize(),
                order.getOrientation(),
                order.getPageRange(),
                order.isDoubleSided(),
                order.getPricePerPage(),
                order.getTotalAmount(),
                "INR",
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getPrintedAt(),
                attempts);
    }

    private AdminPrintJobAttempt toAttempt(PrintJobEntity job) {
        return new AdminPrintJobAttempt(
                job.getId(),
                job.getAttemptNumber(),
                job.getStatus(),
                job.getErrorCode(),
                job.getErrorMessage(),
                job.getQueuedAt(),
                job.getStartedAt(),
                job.getCompletedAt());
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String normalized = search.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_SEARCH_LENGTH) {
            throw new InvalidAdminOrderQueryException("Search text must not exceed 100 characters.");
        }
        return normalized;
    }
}
