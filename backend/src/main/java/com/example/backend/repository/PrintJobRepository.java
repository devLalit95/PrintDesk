package com.example.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

import com.example.backend.entity.PrintJobEntity;
import com.example.backend.entity.PrintJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface PrintJobRepository extends JpaRepository<PrintJobEntity, UUID> {

    List<PrintJobEntity> findAllByPrintOrder_IdOrderByAttemptNumberDesc(UUID printOrderId);

    boolean existsByPrintOrder_Document_IdAndAgent_AgentCode(UUID documentId, String agentCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"printOrder", "printOrder.document", "printer", "agent"})
    Optional<PrintJobEntity> findFirstByPrinter_IdAndStatusOrderByQueuedAtAsc(
            UUID printerId, PrintJobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"printOrder", "printOrder.document", "printer", "agent"})
    Optional<PrintJobEntity> findFirstByPrinter_IdAndStatusInOrderByQueuedAtAsc(
            UUID printerId, Collection<PrintJobStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"printOrder", "printOrder.document", "printer", "agent"})
    Optional<PrintJobEntity> findFirstByAgent_IdAndStatusInOrderByQueuedAtAsc(
            UUID agentId, Collection<PrintJobStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"printOrder", "printOrder.document", "printer", "agent"})
    Optional<PrintJobEntity> findFirstByAgent_IdAndPrinter_IdAndStatusOrderByQueuedAtAsc(
            UUID agentId, UUID printerId, PrintJobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"printOrder", "printOrder.document", "printer", "agent"})
    @org.springframework.data.jpa.repository.Query(
            "select j from PrintJobEntity j join fetch j.printOrder o join fetch o.document "
                    + "left join fetch j.printer left join fetch j.agent where j.id = :jobId")
    Optional<PrintJobEntity> findByIdForUpdate(@Param("jobId") UUID jobId);

    Optional<PrintJobEntity> findFirstByPrintOrder_IdOrderByAttemptNumberDesc(UUID orderId);

    Page<PrintJobEntity> findAllByStatusOrderByQueuedAtAsc(
            PrintJobStatus status,
            Pageable pageable);
}
