package com.example.printagent.backend;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.example.printagent.config.AgentConfiguration;
import com.example.printagent.printer.PrinterDescriptor;
import com.example.printagent.printing.PrintJobRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class AgentApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_ERROR_BODY_BYTES = 16 * 1024;

    private final AgentConfiguration configuration;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AgentApiClient(AgentConfiguration configuration) {
        this(configuration, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    AgentApiClient(AgentConfiguration configuration, HttpClient httpClient, ObjectMapper objectMapper) {
        this.configuration = configuration;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public AgentToken authenticate() {
        Credentials credentials = new Credentials(configuration.agentCode(), configuration.agentSecret());
        HttpResponse<byte[]> response = sendJson(
                "POST", "/api/agents/authenticate", null, credentials, HttpResponse.BodyHandlers.ofByteArray());
        requireStatus(response, 200);
        return read(response.body(), AgentToken.class);
    }

    public AgentRuntimeConfiguration fetchConfiguration(String accessToken) {
        HttpResponse<byte[]> response = sendJson(
                "GET", "/api/agents/me/config", accessToken, null, HttpResponse.BodyHandlers.ofByteArray());
        requireStatus(response, 200);
        return read(response.body(), AgentRuntimeConfiguration.class);
    }

    public List<RemotePrinter> heartbeat(String accessToken, List<PrinterDescriptor> printers) {
        HeartbeatRequest request = new HeartbeatRequest(
                Instant.now(),
                printers.stream().map(printer -> new PrinterRegistration(
                        printer.systemName(),
                        printer.displayName(),
                        new PrinterCapabilities(
                                printer.colorSupported(),
                                printer.duplexSupported(),
                                printer.maximumCopies(),
                                printer.paperSizes().stream().sorted().toList())))
                        .toList());
        HttpResponse<byte[]> response = sendJson(
                "PUT", "/api/agents/me/heartbeat", accessToken, request, HttpResponse.BodyHandlers.ofByteArray());
        requireStatus(response, 200);
        return read(response.body(), HeartbeatResponse.class).printers();
    }

    public Optional<RemotePrintJob> claim(String accessToken, UUID printerId) {
        HttpResponse<byte[]> response = sendJson(
                "POST",
                "/api/agents/jobs/claim",
                accessToken,
                new ClaimRequest(printerId),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 204) {
            return Optional.empty();
        }
        requireStatus(response, 200);
        return Optional.of(read(response.body(), RemotePrintJob.class));
    }

    public Path downloadDocument(String accessToken, RemotePrintJob job, Path workDirectory, long maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("The document size limit must be positive.");
        }
        URI uri = endpoint("/api/documents/" + job.documentId());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw apiException(response.statusCode(), body.readNBytes(MAX_ERROR_BODY_BYTES));
                }
                String responseType = response.headers().firstValue("Content-Type").orElse("")
                        .split(";", 2)[0].trim();
                if (!job.contentType().equalsIgnoreCase(responseType)) {
                    throw new IllegalArgumentException(
                            "The downloaded document type does not match the claimed job.");
                }
                long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (declaredLength == 0 || declaredLength > maxBytes) {
                    throw new IllegalArgumentException(
                            "The downloaded document exceeds the configured size limit.");
                }

                Files.createDirectories(workDirectory);
                Path target = Files.createTempFile(workDirectory, "print-" + job.jobId() + "-", ".document");
                try {
                    setPermissionsIfSupported(target, EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE));
                    long copied = copyBounded(body, target, maxBytes);
                    if (copied == 0) {
                        throw new IllegalArgumentException("The downloaded print document is empty.");
                    }
                    return target;
                } catch (IOException | RuntimeException exception) {
                    Files.deleteIfExists(target);
                    throw exception;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The document download was interrupted.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("The print document could not be downloaded.", exception);
        }
    }

    public EventResponse reportEvent(String accessToken, UUID jobId, JobEvent event) {
        HttpResponse<byte[]> response = sendJson(
                "POST",
                "/api/agents/jobs/" + jobId + "/events",
                accessToken,
                event,
                HttpResponse.BodyHandlers.ofByteArray());
        requireStatus(response, 200);
        return read(response.body(), EventResponse.class);
    }

    private <T> HttpResponse<T> sendJson(
            String method,
            String path,
            String accessToken,
            Object body,
            HttpResponse.BodyHandler<T> bodyHandler) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            try {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)));
            } catch (IOException exception) {
                throw new IllegalStateException("The agent request could not be serialized.", exception);
            }
        }
        try {
            return httpClient.send(builder.build(), bodyHandler);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The backend request was interrupted.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("The PrintDesk backend could not be reached.", exception);
        }
    }

    private URI endpoint(String path) {
        String base = configuration.backendBaseUrl().toString();
        return URI.create(base.endsWith("/") ? base + path.substring(1) : base + path);
    }

    private <T> T read(byte[] body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException exception) {
            throw new IllegalStateException("The backend returned an invalid response.", exception);
        }
    }

    private AgentApiException apiException(int statusCode, byte[] body) {
        String code = null;
        try {
            code = objectMapper.readTree(body).path("code").asText(null);
        } catch (IOException ignored) {
            // The HTTP status remains actionable if a proxy or server returns a non-JSON error body.
        }
        return new AgentApiException(statusCode, code);
    }

    private <T> void requireStatus(HttpResponse<T> response, int expectedStatus) {
        if (response.statusCode() != expectedStatus) {
            byte[] body = response.body() instanceof byte[] bytes ? bytes : new byte[0];
            throw apiException(response.statusCode(), body);
        }
    }

    private long copyBounded(InputStream input, Path target, long maxBytes) throws IOException {
        long copied = 0;
        byte[] buffer = new byte[8192];
        try (var output = Files.newOutputStream(target)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                copied += count;
                if (copied > maxBytes) {
                    throw new IllegalArgumentException("The downloaded print document exceeds the configured size limit.");
                }
                output.write(buffer, 0, count);
            }
        }
        return copied;
    }

    private void setPermissionsIfSupported(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows ACLs are managed by the current user account.
        }
    }

    private record Credentials(String agentCode, String secret) {
    }

    private record HeartbeatRequest(Instant observedAt, List<PrinterRegistration> printers) {
    }

    private record PrinterRegistration(
            String systemName, String displayName, PrinterCapabilities capabilities) {
    }

    private record PrinterCapabilities(
            boolean color, boolean duplex, int maxCopies, List<String> paperSizes) {
    }

    private record ClaimRequest(UUID printerId) {
    }

    private record HeartbeatResponse(
            Instant serverTime, int heartbeatIntervalSeconds, String agentStatus, List<RemotePrinter> printers) {
    }

    public record AgentToken(
            String accessToken, String tokenType, Instant expiresAt, UUID agentId, String agentCode) {
    }

    public record AgentRuntimeConfiguration(
            int heartbeatIntervalSeconds,
            int maxConcurrentJobsPerAgent,
            String webSocketEndpoint,
            String jobDestination,
            long documentMaxBytes) {
    }

    public record RemotePrinter(
            UUID printerId,
            String systemName,
            String displayName,
            boolean enabled,
            PrinterCapabilitiesResponse capabilities) {
    }

    public record PrinterCapabilitiesResponse(
            boolean color, boolean duplex, int maxCopies, List<String> paperSizes) {
    }

    public record RemotePrintJob(
            UUID jobId,
            UUID orderId,
            int attemptNumber,
            UUID documentId,
            String contentType,
            String fileName,
            PrintOptions options,
            String status) {

        public PrintJobRequest toPrintRequest(Path documentPath, String printerSystemName) {
            if (options.pageRange() != null) {
                throw new IllegalArgumentException("Page-range printing is not supported by this agent version.");
            }
            return new PrintJobRequest(
                    jobId,
                    documentPath,
                    contentType,
                    printerSystemName,
                    options.printType(),
                    options.copies(),
                    options.paperSize(),
                    options.orientation(),
                    options.doubleSided());
        }
    }

    public record PrintOptions(
            String printType, int copies, String paperSize, String orientation, boolean doubleSided, String pageRange) {
    }

    public record JobEvent(
            UUID eventId, String status, Instant occurredAt, String errorCode, String errorMessage) {
    }

    public record EventResponse(
            UUID jobId, String status, boolean accepted, boolean duplicate, Instant updatedAt) {
    }

    public record JobAvailableNotification(
            UUID eventId, String eventType, UUID jobId, UUID printerId, Instant occurredAt) {
    }
}
