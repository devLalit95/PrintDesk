package com.example.backend.service.agent;

import java.util.UUID;

import com.example.backend.entity.PrintAgentEntity;
import com.example.backend.entity.PrintAgentStatus;
import com.example.backend.repository.PrintAgentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AgentTokenValidator {

    private final PrintAgentRepository agentRepository;

    public AgentTokenValidator(PrintAgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public PrintAgentEntity requireActiveAgent(Jwt token) {
        try {
            String agentCode = token.getSubject();
            UUID agentId = UUID.fromString(token.getClaimAsString("agentId"));
            if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(java.time.Instant.now())) {
                throw new AccessDeniedException("The agent token has expired.");
            }
            PrintAgentEntity agent = agentRepository.findById(agentId)
                    .orElseThrow(AgentNotFoundException::new);
            if (agent.getStatus() == PrintAgentStatus.REVOKED || !agent.getAgentCode().equals(agentCode)) {
                throw new AccessDeniedException("The agent is not active.");
            }
            return agent;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AccessDeniedException("The agent token is invalid.");
        }
    }
}
