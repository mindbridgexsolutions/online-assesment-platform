package com.mindbridgex.assessment.security;

import com.mindbridgex.assessment.domain.RoleType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Authenticated principal built from a verified JWT plus the cached user snapshot (no DB hit per request). */
public class AuthUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String username;
    private final String fullName;
    private final Set<RoleType> roles;
    private final boolean mustChangePassword;

    public AuthUser(long id, String username, String fullName, Set<RoleType> roles, boolean mustChangePassword) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(RoleType.class) : EnumSet.copyOf(roles);
        // The Super Administrator holds every administrator and teacher permission as well.
        if (this.roles.contains(RoleType.SUPER_ADMIN)) {
            this.roles.add(RoleType.ADMIN);
            this.roles.add(RoleType.TEACHER);
        }
        if (this.roles.contains(RoleType.HOD)) {
            this.roles.add(RoleType.TEACHER);
        }
        this.mustChangePassword = mustChangePassword;
    }

    public long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<RoleType> getRoles() {
        return roles;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isSuperAdmin() {
        return roles.contains(RoleType.SUPER_ADMIN);
    }

    public boolean isAdmin() {
        return roles.contains(RoleType.ADMIN);
    }

    public boolean isTeacher() {
        return roles.contains(RoleType.TEACHER);
    }

    public boolean isHod() {
        return roles.contains(RoleType.HOD) || roles.contains(RoleType.SUPER_ADMIN);
    }

    public boolean isIqac() {
        return roles.contains(RoleType.IQAC);
    }

    public boolean isStudent() {
        return roles.contains(RoleType.STUDENT);
    }

    /** Landing page for the highest-privilege role of the user. */
    public String homePath() {
        if (isSuperAdmin()) {
            return "/super";
        }
        if (isAdmin()) {
            return "/admin";
        }
        if (isTeacher()) {
            return "/teacher";
        }
        if (isIqac()) {
            return "/reports/outcomes";
        }
        return "/student";
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(r -> new SimpleGrantedAuthority(r.getAuthority())).collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
