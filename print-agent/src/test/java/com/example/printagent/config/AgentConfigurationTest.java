package com.example.printagent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentConfigurationTest {

    private static final String SECRET = "test-agent-secret-that-is-longer-than-32-bytes";

    @Test
    void loadsConfigurationFromEnvironmentWithoutExposingTheSecret() {
        AgentConfiguration configuration = AgentConfiguration.fromEnvironment(Map.of(
                "PRINTDESK_AGENT_BACKEND_URL", "https://printdesk.example",
                "PRINTDESK_AGENT_CODE", "PRINT-AGENT-01",
                "PRINTDESK_AGENT_SECRET", SECRET,
                "PRINTDESK_AGENT_WORK_DIRECTORY", "/tmp/printdesk-agent-test"));

        assertEquals("https://printdesk.example", configuration.backendBaseUrl().toString());
        assertEquals("PRINT-AGENT-01", configuration.agentCode());
        assertEquals(Path.of("/tmp/printdesk-agent-test"), configuration.workDirectory());
        org.junit.jupiter.api.Assertions.assertFalse(configuration.toString().contains(SECRET));
    }

    @Test
    void permitsHttpOnlyForLoopbackDevelopment() {
        AgentConfiguration configuration = AgentConfiguration.fromEnvironment(Map.of(
                "PRINTDESK_AGENT_BACKEND_URL", "http://localhost:8080",
                "PRINTDESK_AGENT_CODE", "agent-local",
                "PRINTDESK_AGENT_SECRET", SECRET));

        assertEquals("http", configuration.backendBaseUrl().getScheme());
    }

    @Test
    void rejectsRemoteHttpAndShortSecrets() {
        assertThrows(AgentConfigurationException.class, () -> new AgentConfiguration(
                java.net.URI.create("http://printdesk.example"),
                "agent-01",
                SECRET,
                Path.of("/tmp/agent"),
                "soffice"));
        assertThrows(AgentConfigurationException.class, () -> new AgentConfiguration(
                java.net.URI.create("https://printdesk.example"),
                "agent-01",
                "short",
                Path.of("/tmp/agent"),
                "soffice"));
    }

    @Test
    void rejectsBackendUrlCredentials() {
        assertThrows(AgentConfigurationException.class, () -> new AgentConfiguration(
                java.net.URI.create("https://user:password@printdesk.example"),
                "agent-01",
                SECRET,
                Path.of("/tmp/agent"),
                "soffice"));
    }
}
