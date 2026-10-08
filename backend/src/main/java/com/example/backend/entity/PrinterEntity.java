package com.example.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "printers",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_printers_agent_system_name",
                columnNames = {"agent_id", "system_name"}),
        indexes = {
                @Index(name = "idx_printers_agent_id", columnList = "agent_id"),
                @Index(name = "idx_printers_default_enabled", columnList = "default_printer, enabled")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrinterEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private PrintAgentEntity agent;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "system_name", nullable = false, length = 255)
    private String systemName;

    @Column(name = "default_printer", nullable = false)
    private boolean defaultPrinter;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "supports_color", nullable = false)
    private boolean supportsColor;

    @Column(name = "supports_duplex", nullable = false)
    private boolean supportsDuplex;

    @Column(name = "max_copies", nullable = false)
    private int maxCopies = 1;

    @Column(name = "paper_sizes", nullable = false, length = 512)
    private String paperSizes = "A4";

    @Column(name = "last_discovered_at")
    private Instant lastDiscoveredAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PrinterEntity(PrintAgentEntity agent, String displayName, String systemName) {
        this.agent = agent;
        this.displayName = displayName;
        this.systemName = systemName;
    }

    public void updateDiscovery(String displayName, Instant discoveredAt) {
        this.displayName = displayName;
        this.lastDiscoveredAt = discoveredAt;
    }

    public void updateDiscovery(
            String displayName,
            boolean supportsColor,
            boolean supportsDuplex,
            int maxCopies,
            String paperSizes,
            Instant discoveredAt) {
        updateDiscovery(displayName, discoveredAt);
        this.supportsColor = supportsColor;
        this.supportsDuplex = supportsDuplex;
        this.maxCopies = maxCopies;
        this.paperSizes = paperSizes;
    }

    public void setDefaultPrinter(boolean defaultPrinter) {
        this.defaultPrinter = defaultPrinter;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
