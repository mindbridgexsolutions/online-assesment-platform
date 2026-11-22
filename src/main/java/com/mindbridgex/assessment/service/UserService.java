package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.dto.UserForm;
import com.mindbridgex.assessment.entity.User;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.RefreshTokenRepository;
import com.mindbridgex.assessment.repository.UserRepository;
import com.mindbridgex.assessment.repository.UserSpecs;
import com.mindbridgex.assessment.security.UserLookupService;
import com.mindbridgex.assessment.util.PasswordPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserService {

    /**
     * Result of creating a user: the entity and the clear-text initial password (shown once, never stored).
     */
    public record Created(User user, String initialPassword) {
    }

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final UserLookupService lookup;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public Page<User> search(RoleType role, String q, Boolean active, Pageable pageable) {
        return users.findAll(UserSpecs.search(role, q, active), pageable);
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> AppException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public List<User> activeByRole(RoleType role) {
        return users.findActiveByRole(role);
    }

    @Transactional(readOnly = true)
    public UserForm toForm(Long id) {
        User u = get(id);
        UserForm f = new UserForm();
        f.setId(u.getId());
        f.setUsername(u.getUsername());
        f.setFullName(u.getFullName());
        f.setEmail(u.getEmail());
        f.setRollNo(u.getRollNo());
        f.setPhone(u.getPhone());
        f.setRoles(u.getRoles().isEmpty() ? EnumSet.noneOf(RoleType.class) : EnumSet.copyOf(u.getRoles()));
        f.setActive(u.isActive());
        return f;
    }

    @Transactional
    public Created create(UserForm form) {
        String username = form.getUsername().trim();
        String email = form.getEmail().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw AppException.conflict("Username \'" + username + "\' is already taken.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw AppException.conflict("Email \'" + email + "\' is already registered.");
        }
        boolean generated = form.getPassword() == null || form.getPassword().isBlank();
        String password = generated ? PasswordPolicy.generate() : form.getPassword();
        PasswordPolicy.require(password);
        User u = new User();
        u.setUsername(username);
        u.setEmail(email.toLowerCase(Locale.ROOT));
        u.setFullName(form.getFullName().trim());
        u.setRollNo(blankToNull(form.getRollNo()));
        u.setPhone(blankToNull(form.getPhone()));
        u.setRoles(copy(form.getRoles()));
        u.setActive(form.isActive());
        u.setPasswordHash(encoder.encode(password));
        u.setMustChangePassword(true);
        users.save(u);
        audit.log("USER_CREATED", "User", u.getId(), username + " roles=" + u.getRoles());
        return new Created(u, password);
    }

    @Transactional
    public User update(Long id, UserForm form, Long actingUserId) {
        User u = get(id);
        String email = form.getEmail().trim();
        if (users.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw AppException.conflict("Email \'" + email + "\' is already registered.");
        }
        Set<RoleType> newRoles = copy(form.getRoles());
        boolean rolesChanged = !newRoles.equals(u.getRoles());
        boolean deactivated = u.isActive() && !form.isActive();
        if (id.equals(actingUserId) && (deactivated || (rolesChanged && !newRoles.contains(RoleType.ADMIN)))) {
            throw AppException.bad("You cannot deactivate yourself or remove your own administrator role.");
        }
        if ((deactivated || (rolesChanged && !newRoles.contains(RoleType.ADMIN))) && u.hasRole(RoleType.ADMIN) && users.countActiveByRole(RoleType.ADMIN) <= 1) {
            throw AppException.bad("At least one active administrator must remain.");
        }
        u.setFullName(form.getFullName().trim());
        u.setEmail(email.toLowerCase(Locale.ROOT));
        u.setRollNo(blankToNull(form.getRollNo()));
        u.setPhone(blankToNull(form.getPhone()));
        u.setActive(form.isActive());
        if (rolesChanged) {
            u.getRoles().clear();
            u.getRoles().addAll(newRoles);
            audit.log("USER_ROLES_CHANGED", "User", id, "now " + newRoles);
        }
        if (rolesChanged || deactivated) {
            invalidateSessions(u);
        }
        users.save(u);
        audit.log(deactivated ? "USER_DEACTIVATED" : "USER_UPDATED", "User", id, u.getUsername());
        lookup.evictAll();
        return u;
    }

    @Transactional
    public void setActive(Long id, boolean active, Long actingUserId) {
        User u = get(id);
        if (!active) {
            if (id.equals(actingUserId)) {
                throw AppException.bad("You cannot deactivate your own account.");
            }
            if (u.hasRole(RoleType.ADMIN) && users.countActiveByRole(RoleType.ADMIN) <= 1) {
                throw AppException.bad("At least one active administrator must remain.");
            }
        }
        u.setActive(active);
        if (!active) {
            invalidateSessions(u);
        }
        users.save(u);
        audit.log(active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "User", id, u.getUsername());
        lookup.evictAll();
    }

    /**
     * Admin reset: new random password, must be changed at next sign-in, all sessions revoked.
     */
    @Transactional
    public String resetPassword(Long id) {
        User u = get(id);
        String temp = PasswordPolicy.generate();
        u.setPasswordHash(encoder.encode(temp));
        u.setMustChangePassword(true);
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        invalidateSessions(u);
        users.save(u);
        audit.log("PASSWORD_RESET", "User", id, u.getUsername());
        lookup.evictAll();
        return temp;
    }

    @Transactional
    public void unlock(Long id) {
        User u = get(id);
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        users.save(u);
        audit.log("USER_UNLOCKED", "User", id, u.getUsername());
    }

    /**
     * Self-service password change; every existing session (all devices) is revoked.
     */
    @Transactional
    public void changePassword(Long userId, String current, String next, String confirm) {
        User u = get(userId);
        if (current == null || !encoder.matches(current, u.getPasswordHash())) {
            throw AppException.bad("Your current password is incorrect.");
        }
        if (next == null || !next.equals(confirm)) {
            throw AppException.bad("The new password and its confirmation do not match.");
        }
        if (encoder.matches(next, u.getPasswordHash())) {
            throw AppException.bad("Choose a password different from the current one.");
        }
        PasswordPolicy.require(next);
        u.setPasswordHash(encoder.encode(next));
        u.setMustChangePassword(false);
        invalidateSessions(u);
        users.save(u);
        audit.log("PASSWORD_CHANGED", "User", userId, u.getUsername());
        lookup.evictAll();
    }

    private void invalidateSessions(User u) {
        u.setTokenVersion(u.getTokenVersion() + 1);
        refreshTokens.revokeAllForUser(u.getId(), Instant.now());
    }

    private static Set<RoleType> copy(Set<RoleType> roles) {
        return roles == null || roles.isEmpty() ? EnumSet.noneOf(RoleType.class) : EnumSet.copyOf(roles);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public UserService(final UserRepository users, final RefreshTokenRepository refreshTokens, final PasswordEncoder encoder, final UserLookupService lookup, final AuditService audit) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.lookup = lookup;
        this.audit = audit;
    }
}
