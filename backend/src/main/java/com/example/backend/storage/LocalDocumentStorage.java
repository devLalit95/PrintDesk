package com.example.backend.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class LocalDocumentStorage implements DocumentStorage {

    private static final int BUFFER_SIZE = 8192;

    private final Path root;

    public LocalDocumentStorage(Path root) {
        this.root = Objects.requireNonNull(root, "Storage root is required.")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public StoredDocument store(InputStream content, long maxBytes) throws IOException {
        Objects.requireNonNull(content, "Document content is required.");
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("Maximum document size must be positive.");
        }

        Path temporaryFile = null;
        try {
            Files.createDirectories(root);
            temporaryFile = Files.createTempFile(root, "upload-", ".tmp");

            MessageDigest digest = sha256Digest();
            long sizeBytes = copyAndDigest(content, temporaryFile, digest, maxBytes);
            String storageKey = UUID.randomUUID().toString();
            Path storedFile = resolveStorageKey(storageKey);
            Files.createDirectories(storedFile.getParent());
            Files.move(temporaryFile, storedFile, StandardCopyOption.ATOMIC_MOVE);
            temporaryFile = null;

            return new StoredDocument(storageKey, sizeBytes, HexFormat.of().formatHex(digest.digest()));
        } catch (StorageLimitExceededException exception) {
            cleanTemporaryFile(temporaryFile, exception);
            throw exception;
        } catch (IOException exception) {
            cleanTemporaryFile(temporaryFile, exception);
            throw exception;
        }
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        Path storedFile = resolveStorageKey(storageKey);
        if (!Files.isRegularFile(storedFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Stored document is not available.");
        }
        return Files.newInputStream(
                storedFile,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolveStorageKey(storageKey));
    }

    private long copyAndDigest(
            InputStream content,
            Path temporaryFile,
            MessageDigest digest,
            long maxBytes) throws IOException {
        long sizeBytes = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream output = Files.newOutputStream(
                temporaryFile,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            int bytesRead;
            while ((bytesRead = content.read(buffer)) != -1) {
                if (bytesRead == 0) {
                    continue;
                }
                if (bytesRead > maxBytes - sizeBytes) {
                    throw new StorageLimitExceededException(maxBytes);
                }
                output.write(buffer, 0, bytesRead);
                digest.update(buffer, 0, bytesRead);
                sizeBytes += bytesRead;
            }
        }
        return sizeBytes;
    }

    private Path resolveStorageKey(String storageKey) {
        UUID parsedKey;
        try {
            parsedKey = UUID.fromString(storageKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Storage key is invalid.", exception);
        }
        String canonicalKey = parsedKey.toString();
        if (!canonicalKey.equals(storageKey)) {
            throw new IllegalArgumentException("Storage key is invalid.");
        }

        Path resolved = root.resolve(canonicalKey.substring(0, 2)).resolve(canonicalKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage key is invalid.");
        }
        return resolved;
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void cleanTemporaryFile(Path temporaryFile, Exception originalException) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }
}
