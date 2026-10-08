package com.example.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.PrinterEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrinterRepository extends JpaRepository<PrinterEntity, UUID> {

    Optional<PrinterEntity> findByAgent_IdAndSystemName(UUID agentId, String systemName);

    List<PrinterEntity> findAllByAgent_IdOrderByDisplayNameAsc(UUID agentId);

    Optional<PrinterEntity> findFirstByDefaultPrinterTrueAndEnabledTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PrinterEntity> findByIdAndAgent_Id(UUID id, UUID agentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "agent")
    @Query("select p from PrinterEntity p where p.id = :id")
    Optional<PrinterEntity> findByIdForUpdate(@Param("id") UUID id);
}
