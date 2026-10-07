package com.example.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(
        @NotBlank(message = "A username is required.")
        @Size(max = 64, message = "The username is too long.")
        String username,
        @NotBlank(message = "A password is required.")
        @Size(max = 72, message = "The password is too long.")
        String password) {
}
