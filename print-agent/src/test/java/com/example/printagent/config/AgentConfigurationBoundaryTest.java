package com.example.printagent.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AgentConfigurationBoundaryTest {

    @Test
    void rejectsBackendSubpathsAndSecretsOutsideTheBackendByteLimit() {
        assertThrows(
                AgentConfigurationException.class,
                () -> new AgentConfiguration(
                        URI.create("https://printdesk.example/api"),
                        "agent-01",
                        "01234567890123456789012345678901",
                        Path.of("/tmp/agent"),
                        "soffice"));
        assertThrows(
                AgentConfigurationException.class,
                () -> new AgentConfiguration(
                        URI.create("https://printdesk.example"),
                        "agent-01",
                        "012345678901234567890123456789012345678901234567890123456789012345678901234567890123",
                        Path.of("/tmp/agent"),
                        "soffice"));
    }
}
