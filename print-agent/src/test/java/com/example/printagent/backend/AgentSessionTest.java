package com.example.printagent.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.printagent.config.AgentConfiguration;
import org.junit.jupiter.api.Test;

class AgentSessionTest {

    @Test
    void refreshesAndRetriesAnExpiredBearerTokenOnce() {
        AtomicInteger authentications = new AtomicInteger();
        AtomicInteger configurationRequests = new AtomicInteger();
        AgentConfiguration agentConfiguration = new AgentConfiguration(
                URI.create("https://printdesk.example"),
                "agent-test",
                "01234567890123456789012345678901",
                Path.of("target", "agent-session-test"),
                "soffice");
        AgentApiClient apiClient = new AgentApiClient(agentConfiguration) {
            @Override
            public AgentToken authenticate() {
                int generation = authentications.incrementAndGet();
                return new AgentToken(
                        "jwt-" + generation,
                        "Bearer",
                        Instant.now().plusSeconds(300),
                        UUID.randomUUID(),
                        "agent-test");
            }

            @Override
            public AgentRuntimeConfiguration fetchConfiguration(String accessToken) {
                if (configurationRequests.incrementAndGet() == 1) {
                    throw new AgentApiException(401, "AUTHENTICATION_REQUIRED");
                }
                assertEquals("jwt-2", accessToken);
                return new AgentRuntimeConfiguration(30, 1, "/ws/agents", "/user/queue/jobs", 1024);
            }
        };

        AgentApiClient.AgentRuntimeConfiguration result = new AgentSession(apiClient).fetchConfiguration();

        assertEquals(2, authentications.get());
        assertEquals(2, configurationRequests.get());
        assertEquals("/ws/agents", result.webSocketEndpoint());
    }

    @Test
    void buildsSecureAndLoopbackWebSocketUrisFromTheBackendOrigin() {
        assertEquals(
                URI.create("wss://printdesk.example/ws/agents"),
                AgentStompClient.websocketUri(URI.create("https://printdesk.example"), "/ws/agents"));
        assertEquals(
                URI.create("ws://localhost:8080/ws/agents"),
                AgentStompClient.websocketUri(URI.create("http://localhost:8080"), "/ws/agents"));
    }
}
