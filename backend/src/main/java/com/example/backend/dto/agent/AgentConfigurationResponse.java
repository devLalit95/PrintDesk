package com.example.backend.dto.agent;

public record AgentConfigurationResponse(
        int heartbeatIntervalSeconds,
        int maxConcurrentJobsPerAgent,
        String webSocketEndpoint,
        String jobDestination,
        long documentMaxBytes) {
}
