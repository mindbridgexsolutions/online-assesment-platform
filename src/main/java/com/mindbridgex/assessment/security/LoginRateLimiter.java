package com.mindbridgex.assessment.security;

import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.exception.AppException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter on FAILED logins, keyed by client IP and by username.
 * (Per-node; the database lock-out on the user row protects across nodes.)
 */
@Component
public class LoginRateLimiter {

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();
    private final AppProperties.Login cfg;

    public LoginRateLimiter(AppProperties props) {
        this.cfg = props.getLogin();
    }

    public void checkAllowed(String ip, String username) {
        if (count("ip:" + ip) >= cfg.getMaxPerIp() || count(userKey(username)) >= cfg.getMaxPerUser()) {
            throw AppException.tooMany("Too many failed sign-in attempts. Please wait a few minutes and try again.");
        }
    }

    public void recordFailure(String ip, String username) {
        add("ip:" + ip);
        add(userKey(username));
    }

    public void recordSuccess(String username) {
        failures.remove(userKey(username));
    }

    @Scheduled(fixedDelay = 600_000)
    public void evictStale() {
        long cutoff = System.currentTimeMillis() - window();
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast() < cutoff;
            }
        });
    }

    private String userKey(String username) {
        return "user:" + (username == null ? "" : username.toLowerCase(Locale.ROOT));
    }

    private long window() {
        return Duration.ofMinutes(cfg.getWindowMinutes()).toMillis();
    }

    private int count(String key) {
        Deque<Long> q = failures.get(key);
        if (q == null) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - window();
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < cutoff) {
                q.pollFirst();
            }
            return q.size();
        }
    }

    private void add(String key) {
        Deque<Long> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(System.currentTimeMillis());
        }
    }
}
