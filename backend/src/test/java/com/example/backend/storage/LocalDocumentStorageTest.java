package com.example.backend.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDocumentStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesContentUnderGeneratedKeyAndReturnsSizeAndDigest() throws Exception {
        byte[] content = "printdesk document".getBytes(StandardCharsets.UTF_8);
        LocalDocumentStorage storage = new LocalDocumentStorage(temporaryDirectory.resolve("private"));

        StoredDocument stored = storage.store(new ByteArrayInputStream(content), 100);

        assertEquals(content.length, stored.sizeBytes());
        assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)),
                stored.sha256Hex());
        assertEquals(UUID.fromString(stored.storageKey()).toString(), stored.storageKey());
        try (var storedContent = storage.open(stored.storageKey())) {
            assertArrayEquals(content, storedContent.readAllBytes());
        }
        assertFalse(stored.storageKey().contains(temporaryDirectory.toString()));
    }

    @Test
    void rejectsOversizedStreamAndCleansTemporaryFile() throws IOException {
        LocalDocumentStorage storage = new LocalDocumentStorage(temporaryDirectory.resolve("private"));

        assertThrows(
                StorageLimitExceededException.class,
                () -> storage.store(new ByteArrayInputStream(new byte[] {1, 2, 3}), 2));

        try (Stream<Path> paths = Files.walk(temporaryDirectory)) {
            assertFalse(paths.anyMatch(Files::isRegularFile));
        }
    }

    @Test
    void rejectsNonUuidStorageKeys() {
        LocalDocumentStorage storage = new LocalDocumentStorage(temporaryDirectory.resolve("private"));

        assertThrows(IllegalArgumentException.class, () -> storage.open("../../outside"));
        assertThrows(IllegalArgumentException.class, () -> storage.delete("../outside"));
    }

    @Test
    void removesAStoredDocumentByOpaqueKey() throws Exception {
        LocalDocumentStorage storage = new LocalDocumentStorage(temporaryDirectory.resolve("private"));
        StoredDocument stored = storage.store(new ByteArrayInputStream(new byte[] {7}), 10);

        storage.delete(stored.storageKey());

        assertThrows(IOException.class, () -> storage.open(stored.storageKey()));
        assertTrue(Files.exists(temporaryDirectory.resolve("private")));
    }
}
