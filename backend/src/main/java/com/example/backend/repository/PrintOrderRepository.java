package com.example.backend.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.PrintOrderEntity;
import com.example.backend.entity.PrintOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintOrderRepository extends JpaRepository<PrintOrderEntity, UUID> {

    Optional<PrintOrderEntity> findByToken(String token);

    boolean existsByToken(String token);

    Page<PrintOrderEntity> findAllByStatusOrderByCreatedAtDesc(
            PrintOrderStatus status,
            Pageable pageable);
}
