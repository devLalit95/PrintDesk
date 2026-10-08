package com.example.printagent.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record AgentConfiguration(
        URI backendBaseUrl,
        String agentCode,
        String agentSecret,
        Path workDirectory,
        String libreOfficeCommand) {

    public AgentConfiguration {
        Objects.requireNonNull(backendBaseUrl, "backendBaseUrl");
        Objects.requireNonNull(agentCode, "agentCode");
        Objects.requireNonNull(agentSecret, "agentSecret");
        Objects.requireNonNull(workDirectory, "workDirectory");
        Objects.requireNonNull(libreOfficeCommand, "libreOfficeCommand");
        workDirectory = workDirectory.toAbsolutePath().normalize();

        if (backendBaseUrl.getHost() == null
                || (backendBaseUrl.getRawPath() != null
                        && !backendBaseUrl.getRawPath().isEmpty()
                        && !"/".equals(backendBaseUrl.getRawPath()))) {
            throw new AgentConfigurationException("The backend URL must contain only a host and optional port.");
        }
        String scheme = backendBaseUrl.getScheme();
        boolean secureScheme = "https".equalsIgnoreCase(scheme);
        boolean localDevelopment = "http".equalsIgnoreCase(scheme)
                && backendBaseUrl.getHost() != null
                && (backendBaseUrl.getHost().equalsIgnoreCase("localhost")
                        || backendBaseUrl.getHost().equals("127.0.0.1")
                        || backendBaseUrl.getHost().equals("::1"));
        if (!secureScheme && !localDevelopment) {
            throw new AgentConfigurationException(
                    "The backend URL must use HTTPS; plain HTTP is allowed only for a loopback development host.");
        }
        if (backendBaseUrl.getUserInfo() != null
                || backendBaseUrl.getQuery() != null
                || backendBaseUrl.getFragment() != null) {
            throw new AgentConfigurationException("The backend URL must not contain credentials or query parameters.");
        }
        if (!agentCode.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new AgentConfigurationException("The agent code must contain 1 to 64 safe identifier characters.");
        }
        int secretBytes = agentSecret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < 32 || secretBytes > 72) {
            throw new AgentConfigurationException("The agent secret must contain 32 to 72 UTF-8 bytes.");
        }
        if (libreOfficeCommand.isBlank()) {
            throw new AgentConfigurationException("The LibreOffice command must not be blank.");
        }
    }

    public static AgentConfiguration fromEnvironment(Map<String, String> environment) {
        String backendUrl = required(environment, "PRINTDESK_AGENT_BACKEND_URL");
        String code = required(environment, "PRINTDESK_AGENT_CODE");
        String secret = required(environment, "PRINTDESK_AGENT_SECRET");
        String workDirectory = environment.getOrDefault(
                "PRINTDESK_AGENT_WORK_DIRECTORY",
                Path.of(System.getProperty("user.home"), ".printdesk", "agent-work").toString());
        String officeCommand = environment.getOrDefault("PRINTDESK_AGENT_LIBREOFFICE_COMMAND", "soffice");
        try {
            return new AgentConfiguration(
                    URI.create(backendUrl),
                    code,
                    secret,
                    Path.of(workDirectory),
                    officeCommand);
        } catch (IllegalArgumentException exception) {
            throw new AgentConfigurationException("The configured backend URL or work directory is invalid.", exception);
        }
    }

    @Override
    public String toString() {
        return "AgentConfiguration[backendBaseUrl=" + backendBaseUrl
                + ", agentCode=" + agentCode
                + ", agentSecret=[REDACTED]"
                + ", workDirectory=" + workDirectory
                + ", libreOfficeCommand=" + libreOfficeCommand + "]";
    }

    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new AgentConfigurationException("Missing required environment variable: " + key);
        }
        return value;
    }
}
