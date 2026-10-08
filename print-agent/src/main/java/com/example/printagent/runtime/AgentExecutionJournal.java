package com.example.printagent.runtime;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.example.printagent.backend.AgentApiClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class AgentExecutionJournal {

    private static final String JOURNAL_FILE_NAME = ".active-print-job.json";

    private final Path workDirectory;
    private final Path journalPath;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public AgentExecutionJournal(Path workDirectory) {
        this.workDirectory = workDirectory;
        this.journalPath = workDirectory.resolve(JOURNAL_FILE_NAME);
    }

    public synchronized Optional<JournalState> load() {
        if (!Files.exists(journalPath, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(journalPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("The local print-job journal is not a regular file.");
        }
        try {
            return Optional.of(objectMapper.readValue(journalPath.toFile(), JournalState.class));
        } catch (IOException exception) {
            throw new IllegalStateException("The local print-job journal is unreadable; printing is paused.", exception);
        }
    }

    public synchronized void save(JournalState state) {
        Path temporary = null;
        try {
            Files.createDirectories(workDirectory);
            temporary = Files.createTempFile(workDirectory, ".active-print-job-", ".tmp");
            setPermissionsIfSupported(temporary, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
            byte[] serialized = objectMapper.writeValueAsBytes(state);
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(serialized);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        journalPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, journalPath, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } catch (IOException exception) {
            throw new IllegalStateException("The local print-job state could not be persisted.", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    throw new IllegalStateException("The temporary print-job journal could not be removed.", exception);
                }
            }
        }
    }

    public synchronized void clear() {
        try {
            Files.deleteIfExists(journalPath);
        } catch (IOException exception) {
            throw new IllegalStateException("The completed print-job journal could not be removed.", exception);
        }
    }

    private void setPermissionsIfSupported(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows ACLs are managed by the current user account.
        }
    }

    public enum Phase {
        CLAIMED,
        PRINTING_EVENT_PENDING,
        READY_TO_SUBMIT,
        SUBMISSION_STARTED,
        RESULT_EVENT_PENDING
    }

    public record JournalState(
            AgentApiClient.RemotePrintJob job,
            String printerSystemName,
            Phase phase,
            AgentApiClient.JobEvent pendingEvent) {
    }
}
