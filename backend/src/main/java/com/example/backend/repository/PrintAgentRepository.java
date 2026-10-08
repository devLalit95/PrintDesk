package com.example.backend.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.PrintAgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrintAgentRepository extends JpaRepository<PrintAgentEntity, UUID> {

    Optional<PrintAgentEntity> findByAgentCode(String agentCode);

    boolean existsByAgentCode(String agentCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PrintAgentEntity a where a.id = :id")
    Optional<PrintAgentEntity> findByIdForUpdate(@Param("id") UUID id);
}
