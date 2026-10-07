package com.example.backend.service;

import java.io.InputStream;

public record DocumentDownload(
        String fileName,
        String contentType,
        long sizeBytes,
        InputStream content) {
}
