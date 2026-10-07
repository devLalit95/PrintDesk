package com.example.backend.service.admin;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.example.backend.entity.AdminAccountEntity;
import com.example.backend.entity.AdminRole;
import com.example.backend.repository.AdminAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final int MAXIMUM_BCRYPT_PASSWORD_BYTES = 72;

    private final AdminAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    public AdminBootstrapRunner(
            AdminAccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${printdesk.admin.bootstrap.username:}") String bootstrapUsername,
            @Value("${printdesk.admin.bootstrap.password:}") String bootstrapPassword) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (accountRepository.existsByRole(AdminRole.ADMIN)) {
            return;
        }

        String username = normalizeUsername(bootstrapUsername);
        if (username.isEmpty() || bootstrapPassword == null || bootstrapPassword.isEmpty()) {
            throw new IllegalStateException(
                    "Set PRINTDESK_ADMIN_BOOTSTRAP_USERNAME and PRINTDESK_ADMIN_BOOTSTRAP_PASSWORD "
                            + "when the database has no ADMIN account.");
        }
        if (username.length() > 64) {
            throw new IllegalStateException("The bootstrap administrator username must be at most 64 characters.");
        }

        int passwordBytes = bootstrapPassword.getBytes(StandardCharsets.UTF_8).length;
        if (bootstrapPassword.length() < MINIMUM_PASSWORD_LENGTH
                || passwordBytes > MAXIMUM_BCRYPT_PASSWORD_BYTES) {
            throw new IllegalStateException(
                    "The bootstrap administrator password must be at least 12 characters "
                            + "and no more than 72 UTF-8 bytes.");
        }
        if (accountRepository.findByUsername(username).isPresent()) {
            throw new IllegalStateException(
                    "The bootstrap administrator username is already assigned to a non-admin account.");
        }

        accountRepository.save(new AdminAccountEntity(
                username,
                passwordEncoder.encode(bootstrapPassword),
                AdminRole.ADMIN));
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
