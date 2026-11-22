package com.mindbridgex.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_id")
    private Long actorId;
    @Column(name = "actor_name", length = 120)
    private String actorName;
    @Column(nullable = false, length = 60)
    private String action;
    @Column(name = "entity_type", length = 40)
    private String entityType;
    @Column(name = "entity_id", length = 40)
    private String entityId;
    @Column(columnDefinition = "text")
    private String details;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return this.id;
    }

    public Long getActorId() {
        return this.actorId;
    }

    public String getActorName() {
        return this.actorName;
    }

    public String getAction() {
        return this.action;
    }

    public String getEntityType() {
        return this.entityType;
    }

    public String getEntityId() {
        return this.entityId;
    }

    public String getDetails() {
        return this.details;
    }

    public String getIpAddress() {
        return this.ipAddress;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setActorId(final Long actorId) {
        this.actorId = actorId;
    }

    public void setActorName(final String actorName) {
        this.actorName = actorName;
    }

    public void setAction(final String action) {
        this.action = action;
    }

    public void setEntityType(final String entityType) {
        this.entityType = entityType;
    }

    public void setEntityId(final String entityId) {
        this.entityId = entityId;
    }

    public void setDetails(final String details) {
        this.details = details;
    }

    public void setIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }
}
