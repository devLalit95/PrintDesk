package com.example.backend.controller;

import java.net.URI;
import java.util.UUID;

import com.example.backend.dto.agent.AdminAgentResponse;
import com.example.backend.dto.agent.AgentCredentialRotationRequest;
import com.example.backend.dto.agent.AgentCredentialsRequest;
import com.example.backend.service.agent.AdminAgentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/agents")
public class AdminAgentController {

    private final AdminAgentService adminAgentService;

    public AdminAgentController(AdminAgentService adminAgentService) {
        this.adminAgentService = adminAgentService;
    }

    @PostMapping
    public ResponseEntity<AdminAgentResponse> provision(
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody AgentCredentialsRequest request) {
        AdminAgentResponse response = adminAgentService.provision(
                request, UUID.fromString(token.getSubject()));
        return ResponseEntity.created(URI.create("/api/admin/agents/" + response.agentCode()))
                .body(response);
    }

    @PutMapping("/{agentCode}/credential")
    public ResponseEntity<Void> rotateCredential(
            @AuthenticationPrincipal Jwt token,
            @PathVariable String agentCode,
            @Valid @RequestBody AgentCredentialRotationRequest request) {
        adminAgentService.rotateSecret(agentCode, request, UUID.fromString(token.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{agentCode}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal Jwt token,
            @PathVariable String agentCode) {
        adminAgentService.revoke(agentCode, UUID.fromString(token.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
