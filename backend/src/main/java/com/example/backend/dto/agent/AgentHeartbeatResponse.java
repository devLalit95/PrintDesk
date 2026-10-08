package com.example.backend.dto.agent;

import java.time.Instant;
import java.util.List;

import com.example.backend.entity.PrintAgentStatus;

public record AgentHeartbeatResponse(
        Instant serverTime,
        int heartbeatIntervalSeconds,
        PrintAgentStatus agentStatus,
        List<AgentPrinterResponse> printers) {
}
