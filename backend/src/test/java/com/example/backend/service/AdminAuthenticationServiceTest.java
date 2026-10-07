package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.example.backend.dto.admin.AdminLoginRequest;
import com.example.backend.entity.AdminAccountEntity;
import com.example.backend.entity.AdminRole;
import com.example.backend.repository.AdminAccountRepository;
import com.example.backend.service.admin.InvalidAdminCredentialsException;
import com.example.backend.service.admin.IssuedAccessToken;
import com.example.backend.service.admin.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminAuthenticationServiceTest {

    private final AdminAccountRepository repository = org.mockito.Mockito.mock(AdminAccountRepository.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtTokenService jwtTokenService = org.mockito.Mockito.mock(JwtTokenService.class);
    private final AdminAuthenticationService service = new AdminAuthenticationService(
            repository,
            passwordEncoder,
            jwtTokenService);

    @Test
    void authenticatesEnabledAdminAndReturnsAccessToken() {
        String password = "test-only-admin-password";
        AdminAccountEntity account = new AdminAccountEntity(
                "admin",
                passwordEncoder.encode(password),
                AdminRole.ADMIN);
        Instant expiresAt = Instant.parse("2026-10-08T01:30:00Z");
        when(repository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(jwtTokenService.issueAccessToken(account))
                .thenReturn(new IssuedAccessToken("signed-token", expiresAt));

        var response = service.login(new AdminLoginRequest(" ADMIN ", password));

        assertEquals("signed-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(expiresAt, response.expiresAt());
    }

    @Test
    void usesOneGenericFailureForUnknownUsersAndWrongPasswords() {
        when(repository.findByUsername("unknown")).thenReturn(Optional.empty());
        when(repository.findByUsername("admin")).thenReturn(Optional.of(new AdminAccountEntity(
                "admin",
                passwordEncoder.encode("actual-password"),
                AdminRole.ADMIN)));

        assertThrows(
                InvalidAdminCredentialsException.class,
                () -> service.login(new AdminLoginRequest("unknown", "incorrect-password")));
        assertThrows(
                InvalidAdminCredentialsException.class,
                () -> service.login(new AdminLoginRequest("admin", "incorrect-password")));

        verify(jwtTokenService, never()).issueAccessToken(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesDisabledAccountsWithTheSameGenericCredentialFailure() {
        AdminAccountEntity account = new AdminAccountEntity(
                "admin",
                passwordEncoder.encode("actual-password"),
                AdminRole.ADMIN);
        account.setEnabled(false);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(account));

        assertThrows(
                InvalidAdminCredentialsException.class,
                () -> service.login(new AdminLoginRequest("admin", "actual-password")));

        verify(jwtTokenService, never()).issueAccessToken(org.mockito.ArgumentMatchers.any());
    }
}
