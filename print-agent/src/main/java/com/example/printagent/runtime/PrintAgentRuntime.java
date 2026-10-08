package com.example.printagent.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.example.printagent.backend.AgentApiClient;
import com.example.printagent.backend.AgentSession;
import com.example.printagent.backend.AgentStompClient;
import com.example.printagent.config.AgentConfiguration;
import com.example.printagent.printer.PrinterDescriptor;
import com.example.printagent.printer.PrinterDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class PrintAgentRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrintAgentRuntime.class);
    private static final long POLL_INTERVAL_SECONDS = 5;
    private static final long WEBSOCKET_REAUTHENTICATE_SECONDS = 240;
    private static final long MAX_WEBSOCKET_BACKOFF_SECONDS = 60;

    private final AgentConfiguration agentConfiguration;
    private final PrinterDiscovery printerDiscovery;
    private final AgentSession session;
    private final AgentExecutionJournal journal;
    private final ArrayBlockingQueue<AgentApiClient.JobAvailableNotification> notifications =
            new ArrayBlockingQueue<>(256);

    public PrintAgentRuntime(AgentConfiguration configuration, PrinterDiscovery printerDiscovery) {
        this.agentConfiguration = configuration;
        this.printerDiscovery = printerDiscovery;
        AgentApiClient apiClient = new AgentApiClient(configuration);
        this.session = new AgentSession(apiClient);
        this.journal = new AgentExecutionJournal(configuration.workDirectory());
    }

    private AgentJobProcessor createJobProcessor(long documentMaxBytes) {
        var provider = new com.example.printagent.printer.HostPrintServiceProvider();
        var validator = new com.example.printagent.printing.PrintJobRequestValidator();
        var executor = new com.example.printagent.printing.OsPrintServiceExecutor(
                provider,
                new com.example.printagent.printing.DefaultPrintDocumentPreparer(
                        agentConfiguration.workDirectory(),
                        agentConfiguration.libreOfficeCommand(),
                        Duration.ofSeconds(30)),
                validator,
                Duration.ofSeconds(120));
        return new AgentJobProcessor(
                session,
                journal,
                executor,
                validator,
                agentConfiguration.workDirectory(),
                documentMaxBytes);
    }

    public void run() {
        try (AgentInstanceLock ignored = AgentInstanceLock.acquire(agentConfiguration.workDirectory())) {
            runLocked();
        }
    }

    private void runLocked() {
        List<PrinterDescriptor> discoveredPrinters = printerDiscovery.discover();
        LOGGER.info("Discovered {} host printers for agent {}.", discoveredPrinters.size(), agentConfiguration.agentCode());
        session.authenticate();
        AgentApiClient.AgentRuntimeConfiguration backendConfiguration = session.fetchConfiguration();
        if (backendConfiguration.documentMaxBytes() < 1) {
            throw new IllegalStateException("The backend document size limit must be positive.");
        }
        AgentJobProcessor jobProcessor = createJobProcessor(backendConfiguration.documentMaxBytes());
        AtomicReference<List<AgentApiClient.RemotePrinter>> assignedPrinters =
                new AtomicReference<>(session.heartbeat(discoveredPrinters));
        ScheduledExecutorService heartbeatScheduler =
                startHeartbeat(
                        discoveredPrinters,
                        backendConfiguration.heartbeatIntervalSeconds(),
                        assignedPrinters);

        AgentStompClient webSocket = null;
        String webSocketToken = null;
        long nextWebSocketAttemptNanos = 0;
        long webSocketBackoffSeconds = 5;
        long webSocketConnectedAtNanos = 0;

        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    AgentApiClient.JobAvailableNotification notification =
                            notifications.poll(POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
                    AgentApiClient.AgentToken token = session.currentToken();
                    boolean tokenChanged = !token.accessToken().equals(webSocketToken);
                    boolean connectionExpired = webSocketConnectedAtNanos != 0
                            && System.nanoTime() - webSocketConnectedAtNanos
                                    >= TimeUnit.SECONDS.toNanos(WEBSOCKET_REAUTHENTICATE_SECONDS);
                    boolean disconnected = webSocket != null && !webSocket.isConnected();
                    if ((webSocket == null || tokenChanged || connectionExpired || disconnected)
                            && System.nanoTime() >= nextWebSocketAttemptNanos) {
                        if (webSocket != null) {
                            webSocket.close();
                        }
                        AgentStompClient replacement = new AgentStompClient(
                                new ObjectMapper()
                                        .registerModule(new JavaTimeModule())
                                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
                                notifications::offer);
                        try {
                            replacement.connect(
                                    AgentStompClient.websocketUri(
                                            agentConfiguration.backendBaseUrl(),
                                            backendConfiguration.webSocketEndpoint()),
                                    token.accessToken(),
                                    backendConfiguration.jobDestination());
                            webSocket = replacement;
                            webSocketToken = token.accessToken();
                            webSocketConnectedAtNanos = System.nanoTime();
                            webSocketBackoffSeconds = 5;
                            nextWebSocketAttemptNanos = 0;
                            LOGGER.info("Authenticated agent WebSocket connected.");
                        } catch (IllegalStateException exception) {
                            replacement.close();
                            webSocket = null;
                            webSocketToken = null;
                            nextWebSocketAttemptNanos = System.nanoTime()
                                    + TimeUnit.SECONDS.toNanos(webSocketBackoffSeconds);
                            webSocketBackoffSeconds = Math.min(
                                    MAX_WEBSOCKET_BACKOFF_SECONDS, webSocketBackoffSeconds * 2);
                            LOGGER.warn("Agent WebSocket unavailable; REST polling will continue.", exception);
                        }
                    }

                    jobProcessor.resumeJournaledJob();
                    if (journal.load().isPresent()) {
                        continue;
                    }

                    List<AgentApiClient.RemotePrinter> pollingOrder =
                            orderedPrinters(
                                    assignedPrinters.get(),
                                    notification == null ? null : notification.printerId());
                    for (AgentApiClient.RemotePrinter printer : pollingOrder) {
                        if (!printer.enabled()) {
                            continue;
                        }
                        var claimed = session.claim(printer.printerId());
                        if (claimed.isPresent()) {
                            jobProcessor.process(claimed.orElseThrow(), printer.systemName());
                            break;
                        }
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException exception) {
                    LOGGER.warn("Print-agent work cycle failed; the durable job journal will be resumed before new claims.", exception);
                }
            }
        } finally {
            heartbeatScheduler.shutdownNow();
            if (webSocket != null) {
                webSocket.close();
            }
        }
    }

    private ScheduledExecutorService startHeartbeat(
            List<PrinterDescriptor> discoveredPrinters,
            int configuredIntervalSeconds,
            AtomicReference<List<AgentApiClient.RemotePrinter>> assignedPrinters) {
        int interval = Math.max(5, configuredIntervalSeconds);
        var scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "printdesk-agent-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                assignedPrinters.set(session.heartbeat(discoveredPrinters));
            } catch (RuntimeException exception) {
                LOGGER.warn("Agent heartbeat failed; the next scheduled heartbeat will retry.", exception);
            }
        }, interval, interval, TimeUnit.SECONDS);
        return scheduler;
    }

    private List<AgentApiClient.RemotePrinter> orderedPrinters(
            List<AgentApiClient.RemotePrinter> printers, UUID notifiedPrinterId) {
        Map<UUID, AgentApiClient.RemotePrinter> byId = printers.stream()
                .collect(java.util.stream.Collectors.toMap(
                        AgentApiClient.RemotePrinter::printerId, printer -> printer, (first, ignored) -> first));
        List<AgentApiClient.RemotePrinter> ordered = new ArrayList<>(printers);
        ordered.sort(Comparator
                .comparing((AgentApiClient.RemotePrinter printer) ->
                        notifiedPrinterId == null || !printer.printerId().equals(notifiedPrinterId))
                .thenComparing(AgentApiClient.RemotePrinter::systemName, String.CASE_INSENSITIVE_ORDER));
        if (notifiedPrinterId != null && !byId.containsKey(notifiedPrinterId)) {
            LOGGER.debug("Ignoring a job notification for an unassigned printer {}.", notifiedPrinterId);
        }
        return ordered;
    }
}
