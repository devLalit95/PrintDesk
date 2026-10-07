package com.example.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.PrintRateEntity;
import com.example.backend.entity.PrintType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintRateRepository extends JpaRepository<PrintRateEntity, UUID> {

    Optional<PrintRateEntity> findByPrintTypeAndActiveTrue(PrintType printType);

    List<PrintRateEntity> findAllByActiveTrueOrderByPrintTypeAsc();
}
