package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.entity.User;
import com.mindbridgex.assessment.repository.UserRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Forgot password": a person asks for a reset from the login page, an administrator sees the request and resets the password
 * (the existing reset, which gives a one-time temporary password). Requests are kept in the audit log, so no extra table is needed.
 * The answer to the person is always the same, so the page cannot be used to find out which usernames exist.
 */
@Service
public class PasswordResetRequestService {
    public static final String ACTION = "PASSWORD_RESET_REQUESTED";
    private static final int MAX_PER_IP_PER_HOUR = 10;
    private final UserRepository users;
    private final AuditService audit;
    private final JdbcClient jdbc;
    private final Map<String, long[]> perIp = new ConcurrentHashMap<>();

    public record OpenRequest(Long userId, String username, String fullName, String roles, Instant requestedAt) {
    }

    /** @return false when this IP has asked too often; true otherwise (whether or not the account exists). */
    public boolean request(String identifier, String ip) {
        long hour = Instant.now().getEpochSecond() / 3600;
        long[] w = perIp.compute(String.valueOf(ip), (k, v) -> v == null || v[0] != hour ? new long[] {hour, 1} : new long[] {hour, v[1] + 1});
        if (w[1] > MAX_PER_IP_PER_HOUR) {
            return false;
        }
        if (identifier == null || identifier.isBlank() || identifier.length() > 160) {
            return true;
        }
        String id = identifier.trim();
        User u = users.findByUsernameIgnoreCase(id).or(() -> users.findByEmailIgnoreCase(id)).orElse(null);
        if (u == null || !u.isActive()) {
            return true;
        }
        boolean already = jdbc.sql("select count(*) from audit_logs where action = :a and entity_id = :e and created_at > now() - interval '30 minutes'")
                .param("a", ACTION).param("e", String.valueOf(u.getId())).query(Long.class).single() > 0;
        if (!already) {
            audit.logAs(u.getId(), u.getUsername(), ACTION, "User", u.getId(), "Asked for a password reset from the login page");
        }
        return true;
    }

    /** Requests of the last 14 days that no administrator has answered yet (one per person). */
    public List<OpenRequest> open() {
        return jdbc.sql("select u.id, u.username, u.full_name, coalesce((select string_agg(r.role_code, ', ') from user_roles r where r.user_id = u.id), '') roles, max(a.created_at) at "
                + "from audit_logs a join users u on cast(u.id as varchar) = a.entity_id "
                + "where a.action = :a and a.created_at > now() - interval '14 days' and u.active "
                + "and not exists (select 1 from audit_logs r where r.action = 'PASSWORD_RESET' and r.entity_id = a.entity_id and r.created_at > a.created_at) "
                + "group by u.id, u.username, u.full_name order by max(a.created_at) desc")
                .param("a", ACTION)
                .query((rs, i) -> new OpenRequest(rs.getLong("id"), rs.getString("username"), rs.getString("full_name"), rs.getString("roles").toLowerCase(Locale.ROOT).replace('_', ' '), rs.getTimestamp("at").toInstant()))
                .list();
    }

    public PasswordResetRequestService(final UserRepository users, final AuditService audit, final JdbcClient jdbc) {
        this.users = users;
        this.audit = audit;
        this.jdbc = jdbc;
    }
}
