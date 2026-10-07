package com.example.backend.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.example.backend.dto.admin.AdminLoginRequest;
import com.example.backend.dto.admin.AdminLoginResponse;
import com.example.backend.entity.AdminAccountEntity;
import com.example.backend.repository.AdminAccountRepository;
import com.example.backend.service.admin.InvalidAdminCredentialsException;
import com.example.backend.service.admin.IssuedAccessToken;
import com.example.backend.service.admin.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthenticationService {

    private static final int MAXIMUM_BCRYPT_PASSWORD_BYTES = 72;

    private final AdminAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final String dummyPassword;
    private final String dummyPasswordHash;

    public AdminAuthenticationService(
            AdminAccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.dummyPassword = UUID.randomUUID().toString();
        this.dummyPasswordHash = passwordEncoder.encode(dummyPassword);
    }

    public AdminLoginResponse login(AdminLoginRequest request) {
        String username = request == null || request.username() == null
                ? ""
                : request.username().trim().toLowerCase(Locale.ROOT);
        String password = request == null ? null : request.password();
        Optional<AdminAccountEntity> account = username.isEmpty()
                ? Optional.empty()
                : accountRepository.findByUsername(username);
        boolean eligible = account.filter(AdminAccountEntity::isEnabled).isPresent();
        String passwordHash = eligible
                ? account.orElseThrow().getPasswordHash()
                : dummyPasswordHash;
        boolean passwordWithinBcryptLimit = password != null
                && password.getBytes(StandardCharsets.UTF_8).length <= MAXIMUM_BCRYPT_PASSWORD_BYTES;
        boolean passwordMatches = passwordWithinBcryptLimit
                ? passwordEncoder.matches(password, passwordHash)
                : passwordEncoder.matches(dummyPassword, dummyPasswordHash);

        if (!eligible || !passwordMatches || account.orElseThrow().getRole() == null) {
            throw new InvalidAdminCredentialsException();
        }

        IssuedAccessToken issued = jwtTokenService.issueAccessToken(account.orElseThrow());
        return new AdminLoginResponse(issued.value(), "Bearer", issued.expiresAt());
    }
}
