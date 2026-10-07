package com.example.backend.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.AdminAccountEntity;
import com.example.backend.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<AdminAccountEntity, UUID> {

    Optional<AdminAccountEntity> findByUsername(String username);

    boolean existsByRole(AdminRole role);
}
