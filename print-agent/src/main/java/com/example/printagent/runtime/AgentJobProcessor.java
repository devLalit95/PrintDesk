package com.example.printagent.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import com.example.printagent.backend.AgentApiClient;
import com.example.printagent.backend.AgentSession;
import com.example.printagent.printing.OsPrintServiceExecutor;
import com.example.printagent.printing.PrintExecutionException;
import com.example.printagent.printing.PrintJobRequest;
import com.example.printagent.printing.PrintJobRequestValidator;
import com.example.printagent.printing.PrintJobValidationException;
import com.example.printagent.printing.PrintOutcome;

public class AgentJobProcessor {

    private final AgentSession session;
    private final AgentExecutionJournal journal;
    private final OsPrintServiceExecutor printExecutor;
    private final PrintJobRequestValidator requestValidator;
    private final Path workDirectory;
    private final long maxDocumentBytes;

    public AgentJobProcessor(
            AgentSession session,
            AgentExecutionJournal journal,
            OsPrintServiceExecutor printExecutor,
            PrintJobRequestValidator requestValidator,
            Path workDirectory,
            long maxDocumentBytes) {
        this.session = session;
        this.journal = journal;
        this.printExecutor = printExecutor;
        this.requestValidator = requestValidator;
        this.workDirectory = workDirectory;
        this.maxDocumentBytes = maxDocumentBytes;
    }

    public void process(AgentApiClient.RemotePrintJob job, String printerSystemName) {
        AgentExecutionJournal.JournalState state = journal.load()
                .map(existing -> {
                    if (!existing.job().jobId().equals(job.jobId())) {
                        throw new IllegalStateException(
                                "A different print job is journaled; automatic processing is paused.");
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    AgentExecutionJournal.JournalState claimed = new AgentExecutionJournal.JournalState(
                            job, printerSystemName, AgentExecutionJournal.Phase.CLAIMED, null);
                    journal.save(claimed);
                    return claimed;
                });
        resume(state);
    }

    public void resumeJournaledJob() {
        journal.load().ifPresent(this::resume);
    }

    private void resume(AgentExecutionJournal.JournalState initialState) {
        AgentExecutionJournal.JournalState state = initialState;
        while (true) {
            switch (state.phase()) {
                case CLAIMED -> state = prepareAndAcknowledgePrinting(state);
                case PRINTING_EVENT_PENDING -> state = acknowledgePendingEvent(state);
                case READY_TO_SUBMIT -> state = submitToOperatingSystem(state);
                case SUBMISSION_STARTED -> state = recordUnknownAfterRestart(state);
                case RESULT_EVENT_PENDING -> acknowledgeTerminalEvent(state);
                default -> throw new IllegalStateException("The local print-job journal has an unknown phase.");
            }
            if (state == null) {
                return;
            }
        }
    }

    private AgentExecutionJournal.JournalState prepareAndAcknowledgePrinting(
            AgentExecutionJournal.JournalState state) {
        Path document = null;
        try {
            document = session.downloadDocument(state.job(), workDirectory, maxDocumentBytes);
            PrintJobRequest request = state.job().toPrintRequest(document, state.printerSystemName());
            requestValidator.validate(request);
        } catch (PrintJobValidationException | IllegalArgumentException exception) {
            return queueTerminalEvent(state, "FAILED", "JOB_VALIDATION_FAILED", exception.getMessage());
        } finally {
            deleteTemporaryDocument(document);
        }

        AgentApiClient.JobEvent event = event("PRINTING", null, null);
        AgentExecutionJournal.JournalState pending = withPhase(
                state, AgentExecutionJournal.Phase.PRINTING_EVENT_PENDING, event);
        journal.save(pending);
        return acknowledgePendingEvent(pending);
    }

    private AgentExecutionJournal.JournalState acknowledgePendingEvent(
            AgentExecutionJournal.JournalState state) {
        AgentApiClient.EventResponse response =
                session.reportEvent(state.job().jobId(), state.pendingEvent());
        if (!response.accepted()) {
            throw new IllegalStateException("The backend did not accept the print-job lifecycle event.");
        }
        AgentExecutionJournal.JournalState ready = withPhase(
                state, AgentExecutionJournal.Phase.READY_TO_SUBMIT, null);
        journal.save(ready);
        return ready;
    }

    private AgentExecutionJournal.JournalState submitToOperatingSystem(
            AgentExecutionJournal.JournalState state) {
        Path document = session.downloadDocument(state.job(), workDirectory, maxDocumentBytes);
        try {
            PrintJobRequest request = state.job().toPrintRequest(document, state.printerSystemName());
            requestValidator.validate(request);
            AgentExecutionJournal.JournalState submissionStarted = withPhase(
                    state, AgentExecutionJournal.Phase.SUBMISSION_STARTED, null);
            journal.save(submissionStarted);
            PrintOutcome outcome;
            try {
                outcome = printExecutor.execute(request);
            } catch (PrintJobValidationException exception) {
                return queueTerminalEvent(
                        submissionStarted, "FAILED", "JOB_VALIDATION_FAILED", exception.getMessage());
            } catch (PrintExecutionException exception) {
                if (exception.submissionMayHaveOccurred()) {
                    return queueTerminalEvent(
                            submissionStarted,
                            "OUTCOME_UNKNOWN",
                            "PRINT_SUBMISSION_UNCERTAIN",
                            "The operating-system print result could not be confirmed; administrator review is required.");
                }
                return queueTerminalEvent(
                        submissionStarted,
                        "FAILED",
                        "PRINT_PREPARATION_FAILED",
                        "The document could not be prepared for the selected printer.");
            }
            return queueOutcome(submissionStarted, outcome);
        } catch (PrintJobValidationException | IllegalArgumentException exception) {
            return queueTerminalEvent(state, "FAILED", "JOB_VALIDATION_FAILED", exception.getMessage());
        } finally {
            deleteTemporaryDocument(document);
        }
    }

    private AgentExecutionJournal.JournalState recordUnknownAfterRestart(
            AgentExecutionJournal.JournalState state) {
        return queueTerminalEvent(
                state,
                "OUTCOME_UNKNOWN",
                "AGENT_RESTART_DURING_PRINT",
                "The agent restarted after print submission may have started; administrator review is required.");
    }

    private AgentExecutionJournal.JournalState queueOutcome(
            AgentExecutionJournal.JournalState state, PrintOutcome outcome) {
        return switch (outcome.status()) {
            case OS_JOB_COMPLETED -> queueTerminalEvent(
                    state, "PRINTED", null, null);
            case FAILED -> queueTerminalEvent(
                    state, "FAILED", "OS_PRINT_FAILED", outcome.message());
            case OUTCOME_UNKNOWN -> queueTerminalEvent(
                    state, "OUTCOME_UNKNOWN", "OS_PRINT_OUTCOME_UNKNOWN", outcome.message());
        };
    }

    private AgentExecutionJournal.JournalState queueTerminalEvent(
            AgentExecutionJournal.JournalState state,
            String status,
            String errorCode,
            String errorMessage) {
        AgentApiClient.JobEvent event = event(status, errorCode, errorMessage);
        AgentExecutionJournal.JournalState pending = withPhase(
                state, AgentExecutionJournal.Phase.RESULT_EVENT_PENDING, event);
        journal.save(pending);
        acknowledgeTerminalEvent(pending);
        return null;
    }

    private void acknowledgeTerminalEvent(AgentExecutionJournal.JournalState state) {
        AgentApiClient.EventResponse response =
                session.reportEvent(state.job().jobId(), state.pendingEvent());
        if (!response.accepted()) {
            throw new IllegalStateException("The backend did not accept the terminal print-job event.");
        }
        journal.clear();
    }

    private AgentApiClient.JobEvent event(String status, String errorCode, String errorMessage) {
        return new AgentApiClient.JobEvent(UUID.randomUUID(), status, Instant.now(), errorCode, errorMessage);
    }

    private AgentExecutionJournal.JournalState withPhase(
            AgentExecutionJournal.JournalState state,
            AgentExecutionJournal.Phase phase,
            AgentApiClient.JobEvent pendingEvent) {
        return new AgentExecutionJournal.JournalState(
                state.job(), state.printerSystemName(), phase, pendingEvent);
    }

    private void deleteTemporaryDocument(Path document) {
        if (document == null) {
            return;
        }
        try {
            Files.deleteIfExists(document);
        } catch (IOException exception) {
            throw new IllegalStateException("The temporary print document could not be removed.", exception);
        }
    }
}
