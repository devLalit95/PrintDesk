package com.example.backend.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_actor_time", columnList = "actor_id, created_at"),
                @Index(name = "idx_audit_logs_target_time", columnList = "target_type, target_id, created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 24)
    private AuditActorType actorType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "actor_id", columnDefinition = "binary(16)")
    private UUID actorId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "details", length = 2000)
    private String details;

    public AuditLogEntity(
            AuditActorType actorType,
            UUID actorId,
            String action,
            String targetType,
            String targetId,
            String details) {
        this.actorType = actorType;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
    }
}
