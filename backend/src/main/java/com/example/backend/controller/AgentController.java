package com.example.backend.controller;

import java.util.UUID;

import com.example.backend.dto.agent.AgentConfigurationResponse;
import com.example.backend.dto.agent.AgentClaimRequest;
import com.example.backend.dto.agent.AgentCredentialsRequest;
import com.example.backend.dto.agent.AgentHeartbeatRequest;
import com.example.backend.dto.agent.AgentHeartbeatResponse;
import com.example.backend.dto.agent.AgentJobEventRequest;
import com.example.backend.dto.agent.AgentJobEventResponse;
import com.example.backend.dto.agent.AgentPrintJobResponse;
import com.example.backend.dto.agent.AgentTokenResponse;
import com.example.backend.service.agent.AgentAuthenticationService;
import com.example.backend.service.agent.AgentLifecycleService;
import com.example.backend.service.agent.AgentPrintJobService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentAuthenticationService authenticationService;
    private final AgentLifecycleService lifecycleService;
    private final AgentPrintJobService printJobService;

    public AgentController(
            AgentAuthenticationService authenticationService,
            AgentLifecycleService lifecycleService,
            AgentPrintJobService printJobService) {
        this.authenticationService = authenticationService;
        this.lifecycleService = lifecycleService;
        this.printJobService = printJobService;
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AgentTokenResponse> authenticate(
            @Valid @RequestBody AgentCredentialsRequest request) {
        return ResponseEntity.ok(authenticationService.authenticate(request));
    }

    @PutMapping("/me/heartbeat")
    public ResponseEntity<AgentHeartbeatResponse> heartbeat(
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody AgentHeartbeatRequest request) {
        return ResponseEntity.ok(lifecycleService.heartbeat(token, request));
    }

    @GetMapping("/me/config")
    public ResponseEntity<AgentConfigurationResponse> configuration(@AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok(lifecycleService.configuration(token));
    }

    @PostMapping("/jobs/claim")
    public ResponseEntity<AgentPrintJobResponse> claimNext(
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody AgentClaimRequest request) {
        return printJobService.claimNext(token, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/jobs/{jobId}/events")
    public ResponseEntity<AgentJobEventResponse> reportEvent(
            @AuthenticationPrincipal Jwt token,
            @PathVariable UUID jobId,
            @Valid @RequestBody AgentJobEventRequest request) {
        return ResponseEntity.ok(printJobService.reportEvent(token, jobId, request));
    }
}
