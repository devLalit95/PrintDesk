package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "admins",
        uniqueConstraints = @UniqueConstraint(name = "uq_admins_username", columnNames = "username"),
        indexes = @Index(name = "idx_admins_enabled", columnList = "enabled"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccountEntity extends BaseEntity {

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private AdminRole role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public AdminAccountEntity(String username, String passwordHash, AdminRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
