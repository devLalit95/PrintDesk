package com.example.backend.service.document;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "printdesk.documents")
@Validated
public record DocumentProcessingProperties(
        @NotBlank String libreOfficeCommand,
        @Min(1) long docxConversionTimeoutSeconds,
        @Min(1) long maxImagePixels,
        @Min(1) long maxDocxExpandedBytes,
        @Min(1) int maxDocxEntries) {
}
