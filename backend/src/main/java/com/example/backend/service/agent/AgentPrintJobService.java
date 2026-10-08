package com.example.backend.service.agent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.example.backend.dto.agent.AgentClaimRequest;
import com.example.backend.dto.agent.AgentJobEventRequest;
import com.example.backend.dto.agent.AgentJobEventResponse;
import com.example.backend.dto.agent.AgentPrintJobResponse;
import com.example.backend.entity.AuditActorType;
import com.example.backend.entity.AuditLogEntity;
import com.example.backend.entity.PrintAgentEntity;
import com.example.backend.entity.PrintAgentStatus;
import com.example.backend.entity.PrintJobEntity;
import com.example.backend.entity.PrintJobEventEntity;
import com.example.backend.entity.PrintJobStatus;
import com.example.backend.entity.PrintOrderEntity;
import com.example.backend.entity.PrintOrderStatus;
import com.example.backend.entity.PrinterEntity;
import com.example.backend.repository.AuditLogRepository;
import com.example.backend.repository.PrintAgentRepository;
import com.example.backend.repository.PrintJobEventRepository;
import com.example.backend.repository.PrintJobRepository;
import com.example.backend.repository.PrinterRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentPrintJobService {

    private static final List<PrintJobStatus> ACTIVE_STATUSES =
            List.of(PrintJobStatus.CLAIMED, PrintJobStatus.PRINTING);
    private static final Pattern WINDOWS_ABSOLUTE_PATH =
            Pattern.compile("(?i)\\b[A-Z]:\\\\[^\\s,;]+");
    private static final Pattern UNIX_ABSOLUTE_PATH =
            Pattern.compile("(?<![\\w:])/(?:[^\\s/]+/)*[^\\s/,;]+");

    private final AgentTokenValidator tokenValidator;
    private final PrintAgentRepository agentRepository;
    private final PrinterRepository printerRepository;
    private final PrintJobRepository jobRepository;
    private final PrintJobEventRepository eventRepository;
    private final AuditLogRepository auditLogRepository;

    public AgentPrintJobService(
            AgentTokenValidator tokenValidator,
            PrintAgentRepository agentRepository,
            PrinterRepository printerRepository,
            PrintJobRepository jobRepository,
            PrintJobEventRepository eventRepository,
            AuditLogRepository auditLogRepository) {
        this.tokenValidator = tokenValidator;
        this.agentRepository = agentRepository;
        this.printerRepository = printerRepository;
        this.jobRepository = jobRepository;
        this.eventRepository = eventRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public Optional<AgentPrintJobResponse> claimNext(Jwt token, AgentClaimRequest request) {
        if (request == null || request.printerId() == null) {
            throw new InvalidPrintJobOperationException("A printer identifier is required.", false);
        }
        PrintAgentEntity authenticatedAgent = tokenValidator.requireActiveAgent(token);
        PrintAgentEntity agent = agentRepository.findByIdForUpdate(authenticatedAgent.getId())
                .orElseThrow(AgentNotFoundException::new);
        if (agent.getStatus() != PrintAgentStatus.ONLINE) {
            throw new AccessDeniedException("The agent must heartbeat before claiming work.");
        }
        PrinterEntity printer = printerRepository.findByIdAndAgent_Id(request.printerId(), agent.getId())
                .orElseThrow(AgentNotFoundException::new);
        if (!printer.isEnabled()) {
            throw new AccessDeniedException("The selected printer is disabled.");
        }

        Optional<PrintJobEntity> resumableClaim = jobRepository
                .findFirstByAgent_IdAndPrinter_IdAndStatusOrderByQueuedAtAsc(
                        agent.getId(), printer.getId(), PrintJobStatus.CLAIMED);
        if (resumableClaim.isPresent()) {
            return Optional.of(toResponse(resumableClaim.orElseThrow()));
        }

        if (jobRepository.findFirstByPrinter_IdAndStatusInOrderByQueuedAtAsc(
                        printer.getId(), ACTIVE_STATUSES).isPresent()
                || jobRepository.findFirstByAgent_IdAndStatusInOrderByQueuedAtAsc(
                        agent.getId(), ACTIVE_STATUSES).isPresent()) {
            return Optional.empty();
        }

        Optional<PrintJobEntity> queued = jobRepository.findFirstByPrinter_IdAndStatusOrderByQueuedAtAsc(
                printer.getId(), PrintJobStatus.QUEUED);
        if (queued.isEmpty()) {
            return Optional.empty();
        }
        PrintJobEntity job = queued.orElseThrow();
        if (job.getAgent() == null || !job.getAgent().getId().equals(agent.getId())
                || job.getPrintOrder().getStatus() != PrintOrderStatus.QUEUED) {
            throw new InvalidPrintJobOperationException(
                    "The queued job assignment is inconsistent and requires administrator review.", true);
        }
        job.claim(Instant.now());
        jobRepository.saveAndFlush(job);
        return Optional.of(toResponse(job));
    }

    @Transactional
    public AgentJobEventResponse reportEvent(Jwt token, UUID jobId, AgentJobEventRequest request) {
        if (request == null || request.eventId() == null || request.status() == null
                || request.occurredAt() == null) {
            throw new InvalidPrintJobOperationException(
                    "An event identifier, status, and event time are required.", false);
        }
        PrintAgentEntity agent = tokenValidator.requireActiveAgent(token);
        PrintJobEntity job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(PrintJobNotFoundException::new);
        if (job.getAgent() == null || !job.getAgent().getId().equals(agent.getId())) {
            throw new PrintJobNotFoundException();
        }

        validateEvent(request);
        String errorCode = normalizeErrorCode(request.errorCode());
        String errorMessage = sanitizeErrorMessage(request.errorMessage());
        Instant occurredAt = request.occurredAt().truncatedTo(ChronoUnit.MICROS);
        Optional<PrintJobEventEntity> existing = eventRepository.findByJob_IdAndEventId(jobId, request.eventId());
        if (existing.isPresent()) {
            PrintJobEventEntity stored = existing.orElseThrow();
            if (!samePayload(stored, request.status(), occurredAt, errorCode, errorMessage)) {
                throw new IdempotencyKeyReusedException();
            }
            return new AgentJobEventResponse(
                    jobId, stored.getStatus(), true, true, stored.getCreatedAt());
        }

        ensureTransitionAllowed(job.getStatus(), request.status());
        Instant serverTime = Instant.now();
        applyEvent(job, request.status(), errorCode, errorMessage, serverTime);
        jobRepository.saveAndFlush(job);
        PrintJobEventEntity event = eventRepository.saveAndFlush(new PrintJobEventEntity(
                job, request.eventId(), request.status(), occurredAt, errorCode, errorMessage));
        auditLogRepository.save(new AuditLogEntity(
                AuditActorType.PRINT_AGENT,
                agent.getId(),
                "JOB_EVENT_" + request.status().name(),
                "PRINT_JOB",
                jobId.toString(),
                "attempt=" + job.getAttemptNumber() + ";eventId=" + request.eventId()
                        + (errorCode == null ? "" : ";errorCode=" + errorCode)));
        return new AgentJobEventResponse(jobId, job.getStatus(), true, false, event.getCreatedAt());
    }

    private void validateEvent(AgentJobEventRequest request) {
        if (request.status() != PrintJobStatus.PRINTING
                && request.status() != PrintJobStatus.PRINTED
                && request.status() != PrintJobStatus.FAILED
                && request.status() != PrintJobStatus.OUTCOME_UNKNOWN) {
            throw new InvalidPrintJobOperationException("The reported job status is not allowed.", false);
        }
        if (request.occurredAt().isAfter(Instant.now().plusSeconds(60))) {
            throw new InvalidPrintJobOperationException("The event time is too far in the future.", false);
        }
        boolean terminalFailure = request.status() == PrintJobStatus.FAILED
                || request.status() == PrintJobStatus.OUTCOME_UNKNOWN;
        if (terminalFailure && isBlank(request.errorCode()) && isBlank(request.errorMessage())) {
            throw new InvalidPrintJobOperationException(
                    "A failed or uncertain job event must include a safe error detail.", false);
        }
        if (!terminalFailure && (!isBlank(request.errorCode()) || !isBlank(request.errorMessage()))) {
            throw new InvalidPrintJobOperationException(
                    "Error details are only accepted for failed or uncertain job events.", false);
        }
    }

    private void ensureTransitionAllowed(PrintJobStatus current, PrintJobStatus next) {
        if (current == next) {
            return;
        }
        boolean allowed = switch (next) {
            case PRINTING -> current == PrintJobStatus.CLAIMED;
            case PRINTED -> current == PrintJobStatus.PRINTING;
            case FAILED -> current == PrintJobStatus.CLAIMED || current == PrintJobStatus.PRINTING;
            case OUTCOME_UNKNOWN -> current == PrintJobStatus.PRINTING;
            default -> false;
        };
        if (!allowed) {
            throw new InvalidPrintJobOperationException(
                    "The job cannot transition from " + current + " to " + next + ".", true);
        }
    }

    private void applyEvent(
            PrintJobEntity job,
            PrintJobStatus next,
            String errorCode,
            String errorMessage,
            Instant serverTime) {
        if (job.getStatus() == next) {
            return;
        }
        PrintOrderEntity order = job.getPrintOrder();
        switch (next) {
            case PRINTING -> {
                job.start(serverTime);
                order.transitionTo(PrintOrderStatus.PRINTING, serverTime);
            }
            case PRINTED -> {
                job.complete(PrintJobStatus.PRINTED, null, null, serverTime);
                order.transitionTo(PrintOrderStatus.PRINTED, serverTime);
            }
            case FAILED -> {
                job.complete(PrintJobStatus.FAILED, errorCode, errorMessage, serverTime);
                order.transitionTo(PrintOrderStatus.FAILED, serverTime);
            }
            case OUTCOME_UNKNOWN -> {
                job.complete(PrintJobStatus.OUTCOME_UNKNOWN, errorCode, errorMessage, serverTime);
                order.transitionTo(PrintOrderStatus.OUTCOME_UNKNOWN, serverTime);
            }
            default -> throw new InvalidPrintJobOperationException("The job event status is not supported.", false);
        }
    }

    private AgentPrintJobResponse toResponse(PrintJobEntity job) {
        var order = job.getPrintOrder();
        var document = order.getDocument();
        return new AgentPrintJobResponse(
                job.getId(),
                order.getId(),
                job.getAttemptNumber(),
                document.getId(),
                document.getContentType(),
                document.getOriginalFileName(),
                new AgentPrintJobResponse.PrintOptions(
                        order.getPrintType(),
                        order.getCopies(),
                        order.getPaperSize(),
                        order.getOrientation(),
                        order.isDoubleSided(),
                        order.getPageRange()),
                job.getStatus());
    }

    private boolean samePayload(
            PrintJobEventEntity stored,
            PrintJobStatus status,
            Instant occurredAt,
            String errorCode,
            String errorMessage) {
        return stored.getStatus() == status
                && stored.getOccurredAt().equals(occurredAt)
                && java.util.Objects.equals(stored.getErrorCode(), errorCode)
                && java.util.Objects.equals(stored.getErrorMessage(), errorMessage);
    }

    private String normalizeErrorCode(String value) {
        return isBlank(value) ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String sanitizeErrorMessage(String value) {
        if (isBlank(value)) {
            return null;
        }
        String sanitized = value.replaceAll("\\p{Cntrl}", " ").trim();
        sanitized = WINDOWS_ABSOLUTE_PATH.matcher(sanitized).replaceAll("[path]");
        sanitized = UNIX_ABSOLUTE_PATH.matcher(sanitized).replaceAll("[path]");
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
