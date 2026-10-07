package com.example.backend.dto.admin;

import java.time.Instant;

public record AdminLoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt) {
}
