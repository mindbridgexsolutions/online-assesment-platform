package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.entity.RefreshToken;
import com.mindbridgex.assessment.entity.User;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.RefreshTokenRepository;
import com.mindbridgex.assessment.repository.UserRepository;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.security.JwtService;
import com.mindbridgex.assessment.security.LoginRateLimiter;
import com.mindbridgex.assessment.util.Hashing;
import com.mindbridgex.assessment.util.RequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** A rotated token may be presented again for this long (parallel requests racing on the same cookie). */
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(30);

    /** Result of a login/refresh. {@code refreshToken} is null when only a new access token was issued. */
    public record IssuedTokens(String accessToken, String refreshToken, AuthUser principal) {
    }

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginRateLimiter limiter;
    private final AuditService audit;
    private final AppProperties props;
    private final String dummyHash;
    private final TransactionTemplate tx;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtService jwt, LoginRateLimiter limiter, AuditService audit, AppProperties props,
                       PlatformTransactionManager txManager) {
        this.tx = new TransactionTemplate(txManager);
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.limiter = limiter;
        this.audit = audit;
        this.props = props;
        this.dummyHash = encoder.encode("not-a-real-password-" + Hashing.randomToken());
    }

    /**
     * The BCrypt check is deliberately slow (~250 ms of CPU). It runs OUTSIDE any transaction so a burst of
     * logins (e.g. 1000 students at exam start) cannot exhaust the database connection pool.
     * Database work happens in short transactions before and after it; failures are returned, not thrown,
     * from inside the transaction so their counters and audit rows still commit.
     */
    public IssuedTokens login(String username, String password, HttpServletRequest request) {
        String ip = RequestInfo.clientIp(request);
        String uname = username == null ? "" : username.trim();
        limiter.checkAllowed(ip, uname);

        Optional<User> found = uname.isEmpty() ? Optional.empty() : users.findByUsernameIgnoreCase(uname);
        if (found.isEmpty() || !found.get().isActive()) {
            encoder.matches(password == null ? "" : password, dummyHash); // equalise timing
            Long id = found.map(User::getId).orElse(null);
            throw tx.execute(st -> loginFailed(ip, uname, id, "unknown or inactive account"));
        }
        User snapshot = found.get();
        if (snapshot.isLocked()) {
            tx.executeWithoutResult(st -> audit.logAs(snapshot.getId(), snapshot.getUsername(), "LOGIN_BLOCKED",
                    "User", snapshot.getId(), "account locked"));
            throw AppException.unauthorized("This account is temporarily locked. Please try again later.");
        }
        boolean passwordOk = password != null && encoder.matches(password, snapshot.getPasswordHash());

        Object outcome = tx.execute(st -> {
            User user = users.findById(snapshot.getId()).orElseThrow();
            if (!passwordOk) {
                int failed = user.getFailedLogins() + 1;
                if (failed >= props.getLogin().getMaxFailedAttempts()) {
                    user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(props.getLogin().getLockMinutes())));
                    user.setFailedLogins(0);
                    audit.logAs(user.getId(), user.getUsername(), "ACCOUNT_LOCKED", "User", user.getId(),
                            "too many failed logins");
                } else {
                    user.setFailedLogins(failed);
                }
                users.save(user);
                return loginFailed(ip, uname, user.getId(), "bad password");
            }
            user.setFailedLogins(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(Instant.now());
            limiter.recordSuccess(uname);
            audit.logAs(user.getId(), user.getUsername(), "LOGIN_SUCCESS", "User", user.getId(), null);
            return issue(user, request);
        });
        if (outcome instanceof AppException ex) {
            throw ex;
        }
        return (IssuedTokens) outcome;
    }

    /**
     * Rotates a refresh token. Reuse of a token revoked longer ago than the grace window is treated as theft and
     * revokes every session of that user.
     */
    @Transactional
    public Optional<IssuedTokens> refresh(String rawToken, HttpServletRequest request) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshToken> opt = refreshTokens.findByHashForUpdate(Hashing.sha256Hex(rawToken));
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = opt.get();
        Instant now = Instant.now();
        if (token.getExpiresAt().isBefore(now)) {
            return Optional.empty();
        }
        Optional<User> userOpt = users.findById(token.getUserId());
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return Optional.empty();
        }
        User user = userOpt.get();

        if (token.getRevokedAt() != null) {
            boolean benignRace = token.getReplacedByHash() != null
                    && token.getRevokedAt().isAfter(now.minus(ROTATION_GRACE));
            if (benignRace) {
                return Optional.of(new IssuedTokens(jwt.createAccessToken(user), null, toPrincipal(user)));
            }
            refreshTokens.revokeAllForUser(user.getId(), now);
            audit.logAs(user.getId(), user.getUsername(), "TOKEN_REUSE_DETECTED", "User", user.getId(),
                    "all sessions revoked");
            log.warn("Refresh token reuse detected for user {}", user.getId());
            return Optional.empty();
        }

        String newRaw = Hashing.randomToken();
        token.setRevokedAt(now);
        token.setReplacedByHash(Hashing.sha256Hex(newRaw));
        refreshTokens.save(token);
        saveRefreshToken(user, newRaw, request);
        return Optional.of(new IssuedTokens(jwt.createAccessToken(user), newRaw, toPrincipal(user)));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByHashForUpdate(Hashing.sha256Hex(rawRefreshToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(Instant.now());
                refreshTokens.save(t);
            }
        });
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int n = refreshTokens.deleteExpired(Instant.now().minus(Duration.ofDays(1)));
        if (n > 0) {
            log.info("Purged {} expired refresh tokens", n);
        }
    }

    private IssuedTokens issue(User user, HttpServletRequest request) {
        String raw = Hashing.randomToken();
        saveRefreshToken(user, raw, request);
        return new IssuedTokens(jwt.createAccessToken(user), raw, toPrincipal(user));
    }

    private void saveRefreshToken(User user, String raw, HttpServletRequest request) {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(Hashing.sha256Hex(raw));
        rt.setExpiresAt(Instant.now().plus(Duration.ofDays(props.getJwt().getRefreshTtlDays())));
        rt.setUserAgent(RequestInfo.userAgent(request));
        rt.setIpAddress(RequestInfo.clientIp(request));
        refreshTokens.save(rt);
    }

    private AppException loginFailed(String ip, String username, Long userId, String reason) {
        limiter.recordFailure(ip, username);
        audit.logAs(userId, username, "LOGIN_FAILURE", "User", userId, reason);
        return AppException.unauthorized("Invalid username or password.");
    }

    private AuthUser toPrincipal(User u) {
        return new AuthUser(u.getId(), u.getUsername(), u.getFullName(), u.getRoles(), u.isMustChangePassword());
    }
}
