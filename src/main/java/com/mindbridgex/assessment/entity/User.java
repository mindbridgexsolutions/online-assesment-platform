package com.mindbridgex.assessment.entity;

import com.mindbridgex.assessment.domain.RoleType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Column(nullable = false, length = 60)
    private String username;
    @Column(nullable = false, length = 160)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;
    @Column(name = "roll_no", length = 40)
    private String rollNo;
    @Column(length = 30)
    private String phone;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;
    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_code", length = 20)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 100)
    private Set<RoleType> roles = EnumSet.noneOf(RoleType.class);

    public boolean hasRole(RoleType role) {
        return roles.contains(role);
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public String getUsername() {
        return this.username;
    }

    public String getEmail() {
        return this.email;
    }

    public String getPasswordHash() {
        return this.passwordHash;
    }

    public String getFullName() {
        return this.fullName;
    }

    public String getRollNo() {
        return this.rollNo;
    }

    public String getPhone() {
        return this.phone;
    }

    public boolean isActive() {
        return this.active;
    }

    public boolean isMustChangePassword() {
        return this.mustChangePassword;
    }

    public int getFailedLogins() {
        return this.failedLogins;
    }

    public Instant getLockedUntil() {
        return this.lockedUntil;
    }

    public Instant getLastLoginAt() {
        return this.lastLoginAt;
    }

    public int getTokenVersion() {
        return this.tokenVersion;
    }

    public Set<RoleType> getRoles() {
        return this.roles;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public void setPasswordHash(final String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setFullName(final String fullName) {
        this.fullName = fullName;
    }

    public void setRollNo(final String rollNo) {
        this.rollNo = rollNo;
    }

    public void setPhone(final String phone) {
        this.phone = phone;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    public void setMustChangePassword(final boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public void setFailedLogins(final int failedLogins) {
        this.failedLogins = failedLogins;
    }

    public void setLockedUntil(final Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public void setLastLoginAt(final Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public void setTokenVersion(final int tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public void setRoles(final Set<RoleType> roles) {
        this.roles = roles;
    }
}
