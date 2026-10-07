package com.example.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.PrinterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrinterRepository extends JpaRepository<PrinterEntity, UUID> {

    Optional<PrinterEntity> findByAgent_IdAndSystemName(UUID agentId, String systemName);

    List<PrinterEntity> findAllByAgent_IdOrderByDisplayNameAsc(UUID agentId);

    Optional<PrinterEntity> findFirstByDefaultPrinterTrueAndEnabledTrue();
}
