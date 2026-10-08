package com.example.backend.service.agent;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import com.example.backend.dto.agent.AdminAgentResponse;
import com.example.backend.dto.agent.AgentCredentialsRequest;
import com.example.backend.dto.agent.AgentCredentialRotationRequest;
import com.example.backend.entity.AuditActorType;
import com.example.backend.entity.AuditLogEntity;
import com.example.backend.entity.PrintAgentEntity;
import com.example.backend.entity.PrintAgentStatus;
import com.example.backend.repository.AuditLogRepository;
import com.example.backend.repository.PrintAgentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAgentService {

    private final PrintAgentRepository agentRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAgentService(
            PrintAgentRepository agentRepository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder) {
        this.agentRepository = agentRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AdminAgentResponse provision(AgentCredentialsRequest request, UUID actorId) {
        String code = normalizeCode(request.agentCode());
        validateSecret(request.secret());
        if (agentRepository.existsByAgentCode(code)) {
            throw new InvalidAgentOperationException("An agent with this code already exists.", true);
        }
        PrintAgentEntity agent;
        try {
            agent = agentRepository.saveAndFlush(
                    new PrintAgentEntity(code, passwordEncoder.encode(request.secret())));
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new InvalidAgentOperationException("An agent with this code already exists.", true);
        }
        auditLogRepository.save(new AuditLogEntity(
                AuditActorType.ADMIN, actorId, "AGENT_PROVISIONED", "PRINT_AGENT",
                agent.getId().toString(), "agentCode=" + code));
        return toResponse(agent);
    }

    @Transactional
    public void rotateSecret(String agentCode, AgentCredentialRotationRequest request, UUID actorId) {
        PrintAgentEntity agent = findAgent(agentCode);
        if (agent.getStatus() == PrintAgentStatus.REVOKED) {
            throw new InvalidAgentOperationException("A revoked agent cannot be rotated.", true);
        }
        validateSecret(request.secret());
        agent.updateCredentialHash(passwordEncoder.encode(request.secret()));
        auditLogRepository.save(new AuditLogEntity(
                AuditActorType.ADMIN, actorId, "AGENT_CREDENTIAL_ROTATED", "PRINT_AGENT",
                agent.getId().toString(), "agentCode=" + agent.getAgentCode()));
    }

    @Transactional
    public void revoke(String agentCode, UUID actorId) {
        PrintAgentEntity agent = findAgent(agentCode);
        if (agent.getStatus() == PrintAgentStatus.REVOKED) {
            return;
        }
        agent.revoke();
        auditLogRepository.save(new AuditLogEntity(
                AuditActorType.ADMIN, actorId, "AGENT_REVOKED", "PRINT_AGENT",
                agent.getId().toString(), "agentCode=" + agent.getAgentCode()));
    }

    private PrintAgentEntity findAgent(String agentCode) {
        return agentRepository.findByAgentCode(normalizeCode(agentCode))
                .orElseThrow(AgentNotFoundException::new);
    }

    private AdminAgentResponse toResponse(PrintAgentEntity agent) {
        return new AdminAgentResponse(
                agent.getId(), agent.getAgentCode(), agent.getStatus(), agent.getCreatedAt());
    }

    private String normalizeCode(String agentCode) {
        if (agentCode == null) {
            throw new InvalidAgentOperationException("An agent code is required.", false);
        }
        return agentCode.trim().toLowerCase(Locale.ROOT);
    }

    private void validateSecret(String secret) {
        int secretBytes = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < 32 || secretBytes > 72) {
            throw new InvalidAgentOperationException(
                    "The agent secret must contain between 32 and 72 UTF-8 bytes.", false);
        }
    }
}
