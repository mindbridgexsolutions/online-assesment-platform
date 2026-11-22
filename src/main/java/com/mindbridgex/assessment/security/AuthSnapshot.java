package com.mindbridgex.assessment.security;

import com.mindbridgex.assessment.domain.RoleType;

import java.util.Set;

/** Small immutable view of a user used to validate tokens; cached for a few seconds. */
public record AuthSnapshot(long id, String username, String fullName, boolean active, int tokenVersion,
                           boolean mustChangePassword, Set<RoleType> roles) {

    public AuthUser toPrincipal() {
        return new AuthUser(id, username, fullName, roles, mustChangePassword);
    }
}
