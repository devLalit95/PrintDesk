package com.example.backend.service.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.backend.dto.agent.AgentConfigurationResponse;
import com.example.backend.dto.agent.AgentHeartbeatRequest;
import com.example.backend.dto.agent.AgentHeartbeatResponse;
import com.example.backend.dto.agent.AgentPrinterCapabilitiesRequest;
import com.example.backend.dto.agent.AgentPrinterRegistration;
import com.example.backend.dto.agent.AgentPrinterResponse;
import com.example.backend.entity.PrintAgentEntity;
import com.example.backend.entity.PrinterEntity;
import com.example.backend.repository.PrinterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentLifecycleService {

    private static final Set<String> SUPPORTED_PAPER_SIZES = Set.of("A4", "A3", "Letter", "Legal");
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final long MAXIMUM_DOCUMENT_BYTES = 25L * 1024 * 1024;

    private final AgentTokenValidator tokenValidator;
    private final PrinterRepository printerRepository;

    public AgentLifecycleService(
            AgentTokenValidator tokenValidator,
            PrinterRepository printerRepository) {
        this.tokenValidator = tokenValidator;
        this.printerRepository = printerRepository;
    }

    @Transactional
    public AgentHeartbeatResponse heartbeat(
            org.springframework.security.oauth2.jwt.Jwt token,
            AgentHeartbeatRequest request) {
        PrintAgentEntity agent = tokenValidator.requireActiveAgent(token);
        Set<String> systemNames = new HashSet<>();
        List<PrinterEntity> discovered = new ArrayList<>();
        Instant serverTime = Instant.now();

        for (AgentPrinterRegistration registration : request.printers()) {
            String systemName = registration.systemName().trim();
            if (!systemNames.add(systemName)) {
                throw new InvalidAgentOperationException("Printer system names must be unique per heartbeat.", false);
            }
            AgentPrinterCapabilitiesRequest capabilities = registration.capabilities();
            validatePaperSizes(capabilities.paperSizes());
            PrinterEntity printer = printerRepository.findByAgent_IdAndSystemName(agent.getId(), systemName)
                    .orElseGet(() -> new PrinterEntity(agent, registration.displayName().trim(), systemName));
            printer.updateDiscovery(
                    registration.displayName().trim(),
                    capabilities.color(),
                    capabilities.duplex(),
                    capabilities.maxCopies(),
                    String.join(",", capabilities.paperSizes()),
                    serverTime);
            discovered.add(printerRepository.save(printer));
        }
        agent.recordHeartbeat(serverTime);
        return new AgentHeartbeatResponse(
                serverTime,
                HEARTBEAT_INTERVAL_SECONDS,
                agent.getStatus(),
                discovered.stream().map(this::toResponse).toList());
    }

    public AgentConfigurationResponse configuration(org.springframework.security.oauth2.jwt.Jwt token) {
        tokenValidator.requireActiveAgent(token);
        return new AgentConfigurationResponse(
                HEARTBEAT_INTERVAL_SECONDS,
                1,
                "/ws/agents",
                "/user/queue/jobs",
                MAXIMUM_DOCUMENT_BYTES);
    }

    private void validatePaperSizes(List<String> paperSizes) {
        if (paperSizes == null || paperSizes.isEmpty()
                || paperSizes.stream().anyMatch(size -> !SUPPORTED_PAPER_SIZES.contains(size))
                || paperSizes.stream().distinct().count() != paperSizes.size()) {
            throw new InvalidAgentOperationException("One or more printer paper sizes are unsupported.", false);
        }
    }

    private AgentPrinterResponse toResponse(PrinterEntity printer) {
        List<String> paperSizes = List.of(printer.getPaperSizes().split(","));
        return new AgentPrinterResponse(
                printer.getId(),
                printer.getSystemName(),
                printer.getDisplayName(),
                printer.isEnabled(),
                new AgentPrinterResponse.AgentPrinterCapabilitiesResponse(
                        printer.isSupportsColor(),
                        printer.isSupportsDuplex(),
                        printer.getMaxCopies(),
                        paperSizes));
    }
}
