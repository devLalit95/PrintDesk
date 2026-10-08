package com.example.backend.service.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.example.backend.entity.AdminAccountEntity;
import com.example.backend.entity.PrintAgentEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    public static final String DEFAULT_ISSUER = "https://printdesk.local";
    public static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(30);

    private final JwtEncoder jwtEncoder;
    private final String issuer;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${printdesk.security.jwt.issuer:https://printdesk.local}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
    }

    public IssuedAccessToken issueAccessToken(AdminAccountEntity account) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_LIFETIME);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(account.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("username", account.getUsername())
                .claim("authorities", List.of("ROLE_" + account.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedAccessToken(
                jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
                expiresAt);
    }

    public IssuedAccessToken issueAgentAccessToken(PrintAgentEntity agent) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(Duration.ofMinutes(5));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(agent.getAgentCode())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("agentId", agent.getId().toString())
                .claim("authorities", List.of("ROLE_AGENT"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedAccessToken(
                jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
                expiresAt);
    }
}
