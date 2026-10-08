package com.example.backend.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.PrintOrderEntity;
import com.example.backend.entity.PrintOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface PrintOrderRepository extends JpaRepository<PrintOrderEntity, UUID> {

    Optional<PrintOrderEntity> findByToken(String token);

    boolean existsByToken(String token);

    long countByStatus(PrintOrderStatus status);

    @EntityGraph(attributePaths = "document")
    @Query("""
            select o from PrintOrderEntity o
            where (:status is null or o.status = :status)
              and (:search is null
                or lower(o.token) like lower(concat('%', :search, '%'))
                or lower(o.document.originalFileName) like lower(concat('%', :search, '%')))
            """)
    Page<PrintOrderEntity> searchForAdmin(
            @Param("status") PrintOrderStatus status,
            @Param("search") String search,
            Pageable pageable);

    @EntityGraph(attributePaths = "document")
    Optional<PrintOrderEntity> findWithDocumentById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "document")
    @Query("select o from PrintOrderEntity o where o.id = :id")
    Optional<PrintOrderEntity> findWithDocumentByIdForUpdate(@Param("id") UUID id);

    Page<PrintOrderEntity> findAllByStatusOrderByCreatedAtDesc(
            PrintOrderStatus status,
            Pageable pageable);
}
