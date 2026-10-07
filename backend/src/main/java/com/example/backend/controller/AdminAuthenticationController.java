package com.example.backend.controller;

import com.example.backend.dto.admin.AdminLoginRequest;
import com.example.backend.dto.admin.AdminLoginResponse;
import com.example.backend.service.AdminAuthenticationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthenticationController {

    private final AdminAuthenticationService authenticationService;

    public AdminAuthenticationController(AdminAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }
}
