package com.example.backend.service.admin;

import java.time.Instant;
import java.util.UUID;

import com.example.backend.dto.admin.AdminQueueResponse;
import com.example.backend.dto.admin.AdminUnknownOutcomeRequest;
import com.example.backend.dto.admin.AdminPrintRequest;
import com.example.backend.entity.AuditActorType;
import com.example.backend.entity.AuditLogEntity;
import com.example.backend.entity.PrintAgentStatus;
import com.example.backend.entity.PrintJobEntity;
import com.example.backend.entity.PrintJobStatus;
import com.example.backend.entity.PrintOrderEntity;
import com.example.backend.entity.PrintOrderStatus;
import com.example.backend.entity.PrinterEntity;
import com.example.backend.repository.AuditLogRepository;
import com.example.backend.repository.PrintJobRepository;
import com.example.backend.repository.PrintOrderRepository;
import com.example.backend.repository.PrinterRepository;
import com.example.backend.service.agent.AgentNotFoundException;
import com.example.backend.service.agent.InvalidPrintJobOperationException;
import com.example.backend.service.agent.PrintJobNotFoundException;
import com.example.backend.service.agent.PrintJobNotificationPublisher;
import com.example.backend.service.order.PrintOrderNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPrintQueueService {

    public static final int MAX_TOTAL_ATTEMPTS = 3;

    private final PrintOrderRepository orderRepository;
    private final PrintJobRepository jobRepository;
    private final PrinterRepository printerRepository;
    private final AuditLogRepository auditLogRepository;
    private final PrintJobNotificationPublisher notificationPublisher;

    public AdminPrintQueueService(
            PrintOrderRepository orderRepository,
            PrintJobRepository jobRepository,
            PrinterRepository printerRepository,
            AuditLogRepository auditLogRepository,
            PrintJobNotificationPublisher notificationPublisher) {
        this.orderRepository = orderRepository;
        this.jobRepository = jobRepository;
        this.printerRepository = printerRepository;
        this.auditLogRepository = auditLogRepository;
        this.notificationPublisher = notificationPublisher;
    }

    @Transactional
    public AdminQueueResponse queueOrder(UUID orderId, AdminPrintRequest request, UUID actorId) {
        PrintOrderEntity order = orderRepository.findWithDocumentByIdForUpdate(orderId)
                .orElseThrow(PrintOrderNotFoundException::new);
        if (order.getStatus() != PrintOrderStatus.PENDING) {
            throw new InvalidPrintJobOperationException("Only pending orders can be queued for printing.", true);
        }
        PrinterEntity printer = requireAvailablePrinter(request.printerId());
        Instant now = Instant.now();
        order.transitionTo(PrintOrderStatus.PRINT_REQUESTED, now);
        order.transitionTo(PrintOrderStatus.QUEUED, now);
        PrintJobEntity job = jobRepository.saveAndFlush(
                new PrintJobEntity(order, printer, printer.getAgent(), 1, now));
        notificationPublisher.notifyJobAvailable(job);
        orderRepository.saveAndFlush(order);
        auditLogRepository.save(new AuditLogEntity(
                AuditActorType.ADMIN,
                actorId,
                "PRINT_JOB_QUEUED",
                "PRINT_JOB",
                job.getId().toString(),
                "orderId=" + orderId + ";printerId=" + printer.getId() + ";attempt=1"));
        return new AdminQueueResponse(job.getId(), job.getAttemptNumber(), job.getStatus());
    }

    @Transactional
    public AdminQueueResponse retryFailedJob(UUID failedJobId, UUID actorId) {
        PrintJobEntity failedJob = jobRepository.findByIdForUpdate(failedJobId)
                .orElseThrow(PrintJobNotFoundException::new);
        if (failedJob.getStatus() != PrintJobStatus.FAILED
                || failedJob.getPrintOrder().getStatus() != PrintOrderStatus.FAILED) {
            throw new InvalidPrintJobOperationException(
                    "Only failed print jobs with a failed order can be retried.", true);
        }
        PrintJobEntity latest = jobRepository
                .findFirstByPrintOrder_IdOrderByAttemptNumberDesc(failedJob.getPrintOrder().getId())
                .orElseThrow(PrintJobNotFoundException::new);
        if (!latest.getId().equals(failedJobId)) {
            throw new InvalidPrintJobOperationException("Only the latest failed attempt can be retried.", true);
        }
        if (latest.getAttemptNumber() >= MAX_TOTAL_ATTEMPTS) {
            throw new InvalidPrintJobOperationException(
                    "The order has reached its maximum of " + MAX_TOTAL_ATTEMPTS + " total attempts.", true);
        }
        if (failedJob.getPrinter() == null) {
            throw new InvalidPrintJobOperationException("The failed job has no assigned printer.", true);
        }
        PrinterEntity printer = requireAvailablePrinter(failedJob.getPrinter().getId());
        Instant now = Instant.now();
        PrintOrderEntity order = failedJob.getPrintOrder();
        order.transitionTo(PrintOrderStatus.PRINT_REQUESTED, now);
        order.transitionTo(PrintOrderStatus.QUEUED, now);
        PrintJobEntity retry = jobRepository.saveAndFlush(new PrintJobEntity(
                order,
                printer,
                printer.getAgent(),
                latest.getAttemptNumber() + 1,
                now));
        notificationPublisher.notifyJobAvailable(retry);
        orderRepository.saveAndFlush(order);
        auditLogRepository.save(new AuditLogEntity(
                AuditActorType.ADMIN,
                actorId,
                "PRINT_JOB_RETRY_QUEUED",
                "PRINT_JOB",
                retry.getId().toString(),
                "previousJobId=" + failedJobId + ";attempt=" + retry.getAttemptNumber()));
        return new AdminQueueResponse(retry.getId(), retry.getAttemptNumber(), retry.getStatus());
    }

    @Transactional
    public UUID resolveUnknown(
            UUID jobId,
            AdminUnknownOutcomeRequest request,
            UUID actorId) {
        PrintJobEntity job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(PrintJobNotFoundException::new);
        if (request == null || request.decision() == null || request.notes() == null) {
            throw new InvalidPrintJobOperationException("A resolution decision and audit note are required.", false);
        }
        if (job.getStatus() != PrintJobStatus.OUTCOME_UNKNOWN
                || job.getPrintOrder().getStatus() != PrintOrderStatus.OUTCOME_UNKNOWN) {
            throw new InvalidPrintJobOperationException(
                    "Only an uncertain job and order can be adjudicated.", true);
        }
        Instant now = Instant.now();
        PrintOrderEntity order = job.getPrintOrder();
        if (request.decision() == AdminUnknownOutcomeRequest.Decision.CONFIRMED_FAILED) {
            String note = sanitizeAuditNote(request.notes());
            job.complete(
                    PrintJobStatus.FAILED,
                    "ADMIN_CONFIRMED_FAILED",
                    "Administrator confirmed failure: " + note,
                    now);
            order.transitionTo(PrintOrderStatus.FAILED, now);
        } else {
            job.complete(PrintJobStatus.PRINTED, null, null, now);
            order.transitionTo(PrintOrderStatus.PRINTED, now);
        }
        jobRepository.saveAndFlush(job);
        orderRepository.saveAndFlush(order);
        auditLogRepository.save(new AuditLogEntity(
                AuditActorType.ADMIN,
                actorId,
                "PRINT_JOB_OUTCOME_RESOLVED",
                "PRINT_JOB",
                jobId.toString(),
                "decision=" + request.decision() + ";notes=" + sanitizeAuditNote(request.notes())));
        return order.getId();
    }

    private PrinterEntity requireAvailablePrinter(UUID printerId) {
        PrinterEntity printer = printerRepository.findByIdForUpdate(printerId)
                .orElseThrow(AgentNotFoundException::new);
        if (!printer.isEnabled() || printer.getAgent() == null
                || printer.getAgent().getStatus() != PrintAgentStatus.ONLINE) {
            throw new InvalidPrintJobOperationException(
                    "The selected printer is disabled or its agent is offline.", true);
        }
        return printer;
    }

    private String sanitizeAuditNote(String notes) {
        String sanitized = notes.replaceAll("\\p{Cntrl}", " ").trim();
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }
}
