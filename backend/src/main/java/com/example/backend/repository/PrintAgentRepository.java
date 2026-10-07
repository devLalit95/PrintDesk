package com.example.backend.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.PrintAgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintAgentRepository extends JpaRepository<PrintAgentEntity, UUID> {

    Optional<PrintAgentEntity> findByAgentCode(String agentCode);
}
