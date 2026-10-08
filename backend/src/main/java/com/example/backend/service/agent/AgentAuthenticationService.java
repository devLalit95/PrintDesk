package com.example.backend.service.agent;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.example.backend.dto.agent.AgentCredentialsRequest;
import com.example.backend.dto.agent.AgentTokenResponse;
import com.example.backend.entity.PrintAgentEntity;
import com.example.backend.entity.PrintAgentStatus;
import com.example.backend.repository.PrintAgentRepository;
import com.example.backend.service.admin.IssuedAccessToken;
import com.example.backend.service.admin.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AgentAuthenticationService {

    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final int MAXIMUM_BCRYPT_SECRET_BYTES = 72;

    private final PrintAgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final String dummySecret;
    private final String dummySecretHash;

    public AgentAuthenticationService(
            PrintAgentRepository agentRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.agentRepository = agentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.dummySecret = UUID.randomUUID().toString();
        this.dummySecretHash = passwordEncoder.encode(dummySecret);
    }

    public AgentTokenResponse authenticate(AgentCredentialsRequest request) {
        String code = request == null || request.agentCode() == null
                ? ""
                : request.agentCode().trim().toLowerCase(Locale.ROOT);
        String secret = request == null ? null : request.secret();
        Optional<PrintAgentEntity> candidate = code.isEmpty()
                ? Optional.empty()
                : agentRepository.findByAgentCode(code);
        boolean eligible = candidate
                .filter(agent -> agent.getStatus() != PrintAgentStatus.REVOKED)
                .isPresent();
        String encodedSecret = eligible ? candidate.orElseThrow().getCredentialHash() : dummySecretHash;
        boolean secretWithinBcryptLimit = secret != null
                && secret.getBytes(StandardCharsets.UTF_8).length >= MINIMUM_SECRET_BYTES
                && secret.getBytes(StandardCharsets.UTF_8).length <= MAXIMUM_BCRYPT_SECRET_BYTES;
        boolean secretMatches = secretWithinBcryptLimit
                ? passwordEncoder.matches(secret, encodedSecret)
                : passwordEncoder.matches(dummySecret, dummySecretHash);

        if (!eligible || !secretMatches) {
            throw new InvalidAgentCredentialsException();
        }

        PrintAgentEntity agent = candidate.orElseThrow();
        IssuedAccessToken token = jwtTokenService.issueAgentAccessToken(agent);
        return new AgentTokenResponse(
                token.value(),
                "Bearer",
                token.expiresAt(),
                agent.getId(),
                agent.getAgentCode());
    }
}
