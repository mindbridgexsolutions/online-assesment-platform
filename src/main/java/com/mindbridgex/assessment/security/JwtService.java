package com.mindbridgex.assessment.security;

import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public record AccessClaims(long userId, int tokenVersion) {
    }

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;

    public JwtService(AppProperties props) {
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET must be set to a random value of at least 32 characters "
                            + "(e.g. `openssl rand -base64 48`).");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = props.getJwt().getIssuer();
        this.accessTtl = Duration.ofMinutes(props.getJwt().getAccessTtlMinutes());
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("tv", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Returns empty for any invalid, tampered or expired token. */
    public Optional<AccessClaims> parseAccess(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Integer tv = c.get("tv", Integer.class);
            return Optional.of(new AccessClaims(Long.parseLong(c.getSubject()), tv == null ? 0 : tv));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected access token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
