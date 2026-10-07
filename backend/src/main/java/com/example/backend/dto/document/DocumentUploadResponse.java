package com.example.backend.dto.document;

import java.util.UUID;

public record DocumentUploadResponse(
        UUID documentId,
        String fileName,
        String contentType,
        long sizeBytes,
        Integer pageCount) {
}
