package com.example.backend.repository;

import java.util.UUID;

import com.example.backend.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    boolean existsByStorageKey(String storageKey);
}
