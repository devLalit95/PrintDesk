package com.example.backend.storage;

import java.nio.file.Path;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "printdesk.storage")
@Validated
public record DocumentStorageProperties(
        @NotNull Path root,
        @NotNull DataSize maxFileSize) {
}
