package com.example.backend.entity;

import java.time.Instant;

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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "print_agents",
        uniqueConstraints = @UniqueConstraint(name = "uq_print_agents_code", columnNames = "agent_code"),
        indexes = @Index(name = "idx_print_agents_status", columnList = "status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintAgentEntity extends BaseEntity {

    @Column(name = "agent_code", nullable = false, length = 64)
    private String agentCode;

    @Column(name = "credential_hash", nullable = false, length = 255)
    private String credentialHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PrintAgentStatus status = PrintAgentStatus.OFFLINE;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PrintAgentEntity(String agentCode, String credentialHash) {
        this.agentCode = agentCode;
        this.credentialHash = credentialHash;
    }

    public void recordHeartbeat(Instant heartbeatAt) {
        this.lastSeenAt = heartbeatAt;
        this.status = PrintAgentStatus.ONLINE;
    }

    public void updateCredentialHash(String credentialHash) {
        this.credentialHash = credentialHash;
    }

    public void revoke() {
        this.status = PrintAgentStatus.REVOKED;
    }
}
