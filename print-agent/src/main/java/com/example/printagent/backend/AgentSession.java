package com.example.printagent.backend;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.example.printagent.printer.PrinterDescriptor;

public class AgentSession {

    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofSeconds(20);

    private final AgentApiClient client;
    private AgentApiClient.AgentToken token;

    public AgentSession(AgentApiClient client) {
        this.client = client;
    }

    public synchronized AgentApiClient.AgentToken authenticate() {
        token = client.authenticate();
        return token;
    }

    public synchronized AgentApiClient.AgentToken currentToken() {
        accessToken();
        return token;
    }

    public AgentApiClient.AgentRuntimeConfiguration fetchConfiguration() {
        return withAuthentication(client::fetchConfiguration);
    }

    public List<AgentApiClient.RemotePrinter> heartbeat(List<PrinterDescriptor> printers) {
        return withAuthentication(accessToken -> client.heartbeat(accessToken, printers));
    }

    public Optional<AgentApiClient.RemotePrintJob> claim(UUID printerId) {
        return withAuthentication(accessToken -> client.claim(accessToken, printerId));
    }

    public Path downloadDocument(
            AgentApiClient.RemotePrintJob job, Path workDirectory, long maxBytes) {
        return withAuthentication(
                accessToken -> client.downloadDocument(accessToken, job, workDirectory, maxBytes));
    }

    public AgentApiClient.EventResponse reportEvent(
            UUID jobId, AgentApiClient.JobEvent event) {
        return withAuthentication(accessToken -> client.reportEvent(accessToken, jobId, event));
    }

    private <T> T withAuthentication(Function<String, T> operation) {
        String accessToken = accessToken();
        try {
            return operation.apply(accessToken);
        } catch (AgentApiException exception) {
            if (exception.statusCode() != 401) {
                throw exception;
            }
            synchronized (this) {
                token = null;
            }
            return operation.apply(accessToken());
        }
    }

    private synchronized String accessToken() {
        if (token == null || token.expiresAt() == null
                || !token.expiresAt().isAfter(Instant.now().plus(TOKEN_REFRESH_MARGIN))) {
            authenticate();
        }
        return token.accessToken();
    }
}
