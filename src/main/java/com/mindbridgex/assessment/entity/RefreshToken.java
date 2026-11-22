package com.mindbridgex.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "replaced_by_hash", length = 64)
    private String replacedByHash;
    @Column(name = "user_agent", length = 255)
    private String userAgent;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return this.id;
    }

    public Long getUserId() {
        return this.userId;
    }

    public String getTokenHash() {
        return this.tokenHash;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public Instant getRevokedAt() {
        return this.revokedAt;
    }

    public String getReplacedByHash() {
        return this.replacedByHash;
    }

    public String getUserAgent() {
        return this.userAgent;
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

    public void setUserId(final Long userId) {
        this.userId = userId;
    }

    public void setTokenHash(final String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public void setExpiresAt(final Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setRevokedAt(final Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public void setReplacedByHash(final String replacedByHash) {
        this.replacedByHash = replacedByHash;
    }

    public void setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
    }

    public void setIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }
}
