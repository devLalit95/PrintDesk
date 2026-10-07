package com.example.backend.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.AdminAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<AdminAccountEntity, UUID> {

    Optional<AdminAccountEntity> findByUsername(String username);
}
