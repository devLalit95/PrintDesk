package com.example.backend.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.PrintJobEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintJobEventRepository extends JpaRepository<PrintJobEventEntity, UUID> {

    Optional<PrintJobEventEntity> findByJob_IdAndEventId(UUID jobId, UUID eventId);
}
