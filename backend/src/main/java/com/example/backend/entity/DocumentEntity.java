package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "documents",
        uniqueConstraints = @UniqueConstraint(name = "uq_documents_storage_key", columnNames = "storage_key"),
        indexes = @Index(name = "idx_documents_created_at", columnList = "created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentEntity extends BaseEntity {

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "storage_key", nullable = false, length = 128)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 127)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String sha256Hex;

    public DocumentEntity(
            String originalFileName,
            String storageKey,
            String contentType,
            long sizeBytes,
            Integer pageCount,
            String sha256Hex) {
        this.originalFileName = originalFileName;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.pageCount = pageCount;
        this.sha256Hex = sha256Hex;
    }
}
