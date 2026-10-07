package com.example.backend.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.example.backend.entity.AdminAccountEntity;
import com.example.backend.entity.AdminRole;
import com.example.backend.repository.AdminAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminBootstrapRunnerTest {

    private final AdminAccountRepository repository = org.mockito.Mockito.mock(AdminAccountRepository.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Test
    void createsOneAdminFromEnvironmentCredentialsWithAHashedPassword() throws Exception {
        when(repository.existsByRole(AdminRole.ADMIN)).thenReturn(false);
        when(repository.findByUsername("first-admin")).thenReturn(Optional.empty());

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                repository,
                passwordEncoder,
                " First-Admin ",
                "test-only-bootstrap-password");
        runner.run(null);

        ArgumentCaptor<AdminAccountEntity> accountCaptor = ArgumentCaptor.forClass(AdminAccountEntity.class);
        verify(repository).save(accountCaptor.capture());
        AdminAccountEntity created = accountCaptor.getValue();
        assertEquals("first-admin", created.getUsername());
        assertEquals(AdminRole.ADMIN, created.getRole());
        org.junit.jupiter.api.Assertions.assertTrue(
                passwordEncoder.matches("test-only-bootstrap-password", created.getPasswordHash()));
        org.junit.jupiter.api.Assertions.assertNotEquals(
                "test-only-bootstrap-password",
                created.getPasswordHash());
    }

    @Test
    void doesNotReapplyBootstrapCredentialsWhenAnAdminAlreadyExists() throws Exception {
        when(repository.existsByRole(AdminRole.ADMIN)).thenReturn(true);

        new AdminBootstrapRunner(repository, passwordEncoder, "", "").run(null);

        verify(repository, never()).save(any(AdminAccountEntity.class));
    }

    @Test
    void failsStartupClearlyWhenNoAdminExistsAndBootstrapCredentialsAreMissing() {
        when(repository.existsByRole(AdminRole.ADMIN)).thenReturn(false);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(repository, passwordEncoder, "", "");

        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(repository, never()).save(any(AdminAccountEntity.class));
    }

    @Test
    void refusesBootstrapUsernameAlreadyOwnedByAnotherRole() {
        when(repository.existsByRole(AdminRole.ADMIN)).thenReturn(false);
        when(repository.findByUsername("operator")).thenReturn(Optional.of(new AdminAccountEntity(
                "operator",
                passwordEncoder.encode("operator-password"),
                AdminRole.OPERATOR)));

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                repository,
                passwordEncoder,
                "operator",
                "test-only-bootstrap-password");

        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(repository, never()).save(any(AdminAccountEntity.class));
    }
}
