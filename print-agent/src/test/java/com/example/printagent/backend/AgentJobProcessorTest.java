package com.example.printagent.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.printagent.config.AgentConfiguration;
import com.example.printagent.printing.OsPrintServiceExecutor;
import com.example.printagent.printing.PrintDocumentPreparer;
import com.example.printagent.printing.PrintExecutionException;
import com.example.printagent.printing.PrintJobRequest;
import com.example.printagent.printing.PrintJobRequestValidator;
import com.example.printagent.printing.PrintOutcome;
import com.example.printagent.printer.PrintServiceProvider;
import com.example.printagent.runtime.AgentExecutionJournal;
import com.example.printagent.runtime.AgentExecutionJournal.JournalState;
import com.example.printagent.runtime.AgentExecutionJournal.Phase;
import com.example.printagent.runtime.AgentJobProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentJobProcessorTest {

    private Path workDirectory;
    private AgentConfiguration configuration;
    private List<AgentApiClient.JobEvent> reportedEvents;
    private AtomicInteger submissionCount;

    @BeforeEach
    void setUp() throws Exception {
        workDirectory = Files.createTempDirectory("printdesk-agent-processor-test-");
        configuration = new AgentConfiguration(
                URI.create("http://127.0.0.1:8080"),
                "agent-test",
                "01234567890123456789012345678901",
                workDirectory,
                "soffice");
        reportedEvents = new ArrayList<>();
        submissionCount = new AtomicInteger();
    }

    @AfterEach
    void tearDown() throws Exception {
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

    @Test
    void waitsForPrintingAcknowledgementAndReportsTerminalResult() {
        AgentApiClient client = client(event -> new AgentApiClient.EventResponse(
                job().jobId(), event.status(), true, false, Instant.now()));
        AgentExecutionJournal journal = new AgentExecutionJournal(workDirectory);
        AgentJobProcessor processor = processor(client, journal, successfulExecutor());

        processor.process(job(), "Office_Printer");

        assertEquals(List.of("PRINTING", "PRINTED"), reportedEvents.stream()
                .map(AgentApiClient.JobEvent::status)
                .toList());
        assertEquals(1, submissionCount.get());
        assertFalse(journal.load().isPresent());
    }

    @Test
    void replaysTheSamePrintingEventAfterNetworkFailureWithoutLosingJobState() {
        AtomicInteger acknowledgements = new AtomicInteger();
        AgentApiClient client = client(event -> {
            if (acknowledgements.getAndIncrement() == 0) {
                throw new IllegalStateException("backend unavailable");
            }
            return new AgentApiClient.EventResponse(
                    job().jobId(), event.status(), true, true, Instant.now());
        });
        AgentExecutionJournal journal = new AgentExecutionJournal(workDirectory);
        AgentJobProcessor processor = processor(client, journal, successfulExecutor());

        assertThrows(IllegalStateException.class, () -> processor.process(job(), "Office_Printer"));
        JournalState pending = journal.load().orElseThrow();
        assertEquals(Phase.PRINTING_EVENT_PENDING, pending.phase());
        UUID originalEventId = pending.pendingEvent().eventId();

        processor.resumeJournaledJob();

        assertEquals(List.of("PRINTING", "PRINTING", "PRINTED"), reportedEvents.stream()
                .map(AgentApiClient.JobEvent::status)
                .toList());
        assertEquals(originalEventId, reportedEvents.get(0).eventId());
        assertEquals(originalEventId, reportedEvents.get(1).eventId());
        assertEquals(1, submissionCount.get());
        assertFalse(journal.load().isPresent());
    }

    @Test
    void reportsUnknownAfterRestartOnceOsSubmissionMayHaveStarted() {
        AgentApiClient client = client(event -> new AgentApiClient.EventResponse(
                job().jobId(), event.status(), true, false, Instant.now()));
        AgentExecutionJournal journal = new AgentExecutionJournal(workDirectory);
        journal.save(new JournalState(job(), "Office_Printer", Phase.SUBMISSION_STARTED, null));
        AgentJobProcessor processor = processor(client, journal, successfulExecutor());

        processor.resumeJournaledJob();

        assertEquals(List.of("OUTCOME_UNKNOWN"), reportedEvents.stream()
                .map(AgentApiClient.JobEvent::status)
                .toList());
        assertEquals(0, submissionCount.get());
        assertFalse(journal.load().isPresent());
    }

    @Test
    void reportsPreparationFailureWithoutMarkingThePrintOutcomeUnknown() {
        AgentApiClient client = client(event -> new AgentApiClient.EventResponse(
                job().jobId(), event.status(), true, false, Instant.now()));
        AgentExecutionJournal journal = new AgentExecutionJournal(workDirectory);
        OsPrintServiceExecutor preparationFailure = new OsPrintServiceExecutor(
                (PrintServiceProvider) null,
                (PrintDocumentPreparer) null,
                null,
                java.time.Duration.ofSeconds(1)) {
            @Override
            public PrintOutcome execute(PrintJobRequest request) {
                throw new PrintExecutionException("Document preparation failed.");
            }
        };

        processor(client, journal, preparationFailure).process(job(), "Office_Printer");

        assertEquals(List.of("PRINTING", "FAILED"), reportedEvents.stream()
                .map(AgentApiClient.JobEvent::status)
                .toList());
        assertFalse(journal.load().isPresent());
    }

    private AgentApiClient client(
            java.util.function.Function<AgentApiClient.JobEvent, AgentApiClient.EventResponse> eventHandler) {
        AgentApiClient client = new AgentApiClient(configuration) {
            @Override
            public AgentToken authenticate() {
                return new AgentToken("jwt", "Bearer", Instant.now().plusSeconds(300), UUID.randomUUID(), "agent-test");
            }

            @Override
            public Path downloadDocument(
                    String accessToken, RemotePrintJob remoteJob, Path directory, long maxBytes) {
                try {
                    Files.createDirectories(directory);
                    return Files.write(Files.createTempFile(directory, "print-test-", ".pdf"), "%PDF".getBytes());
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }

            @Override
            public EventResponse reportEvent(String accessToken, UUID jobId, JobEvent event) {
                reportedEvents.add(event);
                return eventHandler.apply(event);
            }
        };
        return client;
    }

    private AgentJobProcessor processor(
            AgentApiClient client,
            AgentExecutionJournal journal,
            OsPrintServiceExecutor executor) {
        return new AgentJobProcessor(
                new AgentSession(client),
                journal,
                executor,
                new PrintJobRequestValidator(),
                workDirectory,
                1024);
    }

    private OsPrintServiceExecutor successfulExecutor() {
        return new OsPrintServiceExecutor(
                (PrintServiceProvider) null,
                (PrintDocumentPreparer) null,
                null,
                java.time.Duration.ofSeconds(1)) {
            @Override
            public PrintOutcome execute(PrintJobRequest request) {
                submissionCount.incrementAndGet();
                assertEquals("Office_Printer", request.printerSystemName());
                return new PrintOutcome(PrintOutcome.Status.OS_JOB_COMPLETED, "completed");
            }
        };
    }

    private AgentApiClient.RemotePrintJob job() {
        return new AgentApiClient.RemotePrintJob(
                UUID.fromString("80000000-0000-0000-0000-000000000001"),
                UUID.fromString("80000000-0000-0000-0000-000000000002"),
                1,
                UUID.fromString("80000000-0000-0000-0000-000000000003"),
                "application/pdf",
                "order.pdf",
                new AgentApiClient.PrintOptions("BLACK_AND_WHITE", 1, "A4", "portrait", false, null),
                "CLAIMED");
    }
}
