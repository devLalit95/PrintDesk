package com.example.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.PrintJobEntity;
import com.example.backend.entity.PrintJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintJobRepository extends JpaRepository<PrintJobEntity, UUID> {

    List<PrintJobEntity> findAllByPrintOrder_IdOrderByAttemptNumberDesc(UUID printOrderId);

    Optional<PrintJobEntity> findFirstByPrinter_IdAndStatusOrderByQueuedAtAsc(
            UUID printerId,
            PrintJobStatus status);

    Page<PrintJobEntity> findAllByStatusOrderByQueuedAtAsc(
            PrintJobStatus status,
            Pageable pageable);
}
