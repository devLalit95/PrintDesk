package com.example.backend.dto.agent;

import java.util.List;
import java.util.UUID;

public record AgentPrinterResponse(
        UUID printerId,
        String systemName,
        String displayName,
        boolean enabled,
        AgentPrinterCapabilitiesResponse capabilities) {

    public record AgentPrinterCapabilitiesResponse(
            boolean color,
            boolean duplex,
            int maxCopies,
            List<String> paperSizes) {
    }
}
