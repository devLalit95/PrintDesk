package com.example.backend.dto.document;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record UploadDocumentRequest(
        @NotNull(message = "A document file is required.")
        MultipartFile file) {
}
