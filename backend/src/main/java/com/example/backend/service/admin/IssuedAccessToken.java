package com.example.backend.service.admin;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {
}
