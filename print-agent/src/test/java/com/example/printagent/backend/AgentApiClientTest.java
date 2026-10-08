package com.example.printagent.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.printagent.config.AgentConfiguration;
import com.example.printagent.printer.PrinterDescriptor;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentApiClientTest {

    private HttpServer server;
    private Path workDirectory;
    private AgentConfiguration configuration;
    private AgentApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        workDirectory = Files.createTempDirectory("printdesk-agent-client-test-");
        configuration = new AgentConfiguration(
                java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "agent-test",
                "01234567890123456789012345678901",
                workDirectory,
                "soffice");
        client = new AgentApiClient(configuration);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop(0);
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
    void authenticatesWithTheProvisionedIdentityAndParsesExpiry() {
        Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
        server.createContext("/api/agents/authenticate", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(request.contains("\"agentCode\":\"agent-test\""));
            assertTrue(request.contains("\"secret\":\"01234567890123456789012345678901\""));
            respond(exchange, 200, """
                    {"accessToken":"token-value","tokenType":"Bearer","expiresAt":"2030-01-01T00:00:00Z",
                     "agentId":"b7bcecef-145f-4d25-8883-9e39f65af7cf","agentCode":"agent-test"}
                    """);
        });

        AgentApiClient.AgentToken token = client.authenticate();

        assertEquals("token-value", token.accessToken());
        assertEquals(expiresAt, token.expiresAt());
        assertEquals("agent-test", token.agentCode());
    }

    @Test
    void heartbeatAndClaimUseAuthenticatedContractPayloads() {
        UUID printerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        server.createContext("/api/agents/me/heartbeat", exchange -> {
            assertEquals("Bearer jwt", exchange.getRequestHeaders().getFirst("Authorization"));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"systemName\":\"Office_Printer\""));
            assertTrue(body.contains("\"paperSizes\":[\"A4\",\"Letter\"]"));
            respond(exchange, 200, """
                    {"serverTime":"2030-01-01T00:00:00Z","heartbeatIntervalSeconds":30,"agentStatus":"ONLINE",
                     "printers":[{"printerId":"%s","systemName":"Office_Printer","displayName":"Office Printer",
                     "enabled":true,"capabilities":{"color":true,"duplex":true,"maxCopies":10,
                     "paperSizes":["A4","Letter"]}}]}
                    """.formatted(printerId));
        });
        server.createContext("/api/agents/jobs/claim", exchange -> {
            assertEquals("Bearer jwt", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("""
                    {"printerId":"%s"}""".formatted(printerId),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"jobId":"%s","orderId":"80000000-0000-0000-0000-000000000001","attemptNumber":1,
                     "documentId":"80000000-0000-0000-0000-000000000002","contentType":"application/pdf",
                     "fileName":"order.pdf","options":{"printType":"BLACK_AND_WHITE","copies":2,
                     "paperSize":"A4","orientation":"portrait","doubleSided":false,"pageRange":null},
                     "status":"CLAIMED"}
                    """.formatted(jobId));
        });
        PrinterDescriptor descriptor = new PrinterDescriptor(
                "Office_Printer", "Office Printer", true, true, 10, java.util.Set.of("Letter", "A4"));

        List<AgentApiClient.RemotePrinter> printers = client.heartbeat("jwt", List.of(descriptor));
        var job = client.claim("jwt", printers.getFirst().printerId()).orElseThrow();

        assertEquals(printerId, printers.getFirst().printerId());
        assertEquals(jobId, job.jobId());
        assertEquals("BLACK_AND_WHITE", job.options().printType());
    }

    @Test
    void downloadsOnlyMatchingContentWithinTheConfiguredSizeLimit() throws Exception {
        UUID documentId = UUID.randomUUID();
        var job = new AgentApiClient.RemotePrintJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                documentId,
                "application/pdf",
                "order.pdf",
                new AgentApiClient.PrintOptions("BLACK_AND_WHITE", 1, "A4", "portrait", false, null),
                "CLAIMED");
        server.createContext("/api/documents/" + documentId, exchange -> {
            assertEquals("Bearer jwt", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] document = "%PDF".getBytes(StandardCharsets.US_ASCII);
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, document.length);
            exchange.getResponseBody().write(document);
            exchange.close();
        });

        Path downloaded = client.downloadDocument("jwt", job, workDirectory, 16);

        assertEquals("%PDF", Files.readString(downloaded));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.downloadDocument("jwt", job, workDirectory, 3));
        try (var files = Files.list(workDirectory)) {
            assertEquals(1, files.count(), "The rejected oversized download must be deleted.");
        }
    }

    @Test
    void exposesOnlyTheSafeProblemCodeForBackendErrors() {
        server.createContext("/api/agents/me/config", exchange -> respond(exchange, 401, """
                {"code":"AUTHENTICATION_REQUIRED","detail":"sensitive internal detail"}
                """));

        AgentApiException exception = assertThrows(
                AgentApiException.class,
                () -> client.fetchConfiguration("expired"));

        assertEquals(401, exception.statusCode());
        assertEquals("AUTHENTICATION_REQUIRED", exception.errorCode());
        assertFalse(exception.getMessage().contains("sensitive"));
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
