package com.example.backend.repository;

import java.util.UUID;

import com.example.backend.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    Page<AuditLogEntity> findAllByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);
}
