package com.example.backend.storage;

public record StoredDocument(String storageKey, long sizeBytes, String sha256Hex) {
}
