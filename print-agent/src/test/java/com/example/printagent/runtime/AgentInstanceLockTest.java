package com.example.printagent.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AgentInstanceLockTest {

    @Test
    void preventsTwoAgentProcessesFromSharingAnExecutionJournal() throws Exception {
        Path workDirectory = Files.createTempDirectory("printdesk-agent-lock-test-");
        try {
            AgentInstanceLock first = AgentInstanceLock.acquire(workDirectory);
            assertThrows(IllegalStateException.class, () -> AgentInstanceLock.acquire(workDirectory));

            first.close();

            assertDoesNotThrow(() -> {
                try (AgentInstanceLock ignored = AgentInstanceLock.acquire(workDirectory)) {
                    // Lock acquisition is the assertion.
                }
            });
        } finally {
            try (var paths = Files.list(workDirectory)) {
                paths.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
            Files.deleteIfExists(workDirectory);
        }
    }

    @Test
    void refusesFilesystemAndHomeRootsAsTheAgentWorkDirectory() {
        Path filesystemRoot = Path.of("").toAbsolutePath().getRoot();
        Path homeDirectory = Path.of(System.getProperty("user.home"));

        assertThrows(IllegalStateException.class, () -> AgentInstanceLock.acquire(filesystemRoot));
        assertThrows(IllegalStateException.class, () -> AgentInstanceLock.acquire(homeDirectory));
    }
}
