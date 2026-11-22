package com.mindbridgex.assessment.security;

import com.mindbridgex.assessment.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieUtil {

    public static final String ACCESS_COOKIE = "ACCESS_TOKEN";
    public static final String REFRESH_COOKIE = "REFRESH_TOKEN";

    private final AppProperties props;

    public CookieUtil(AppProperties props) {
        this.props = props;
    }

    public void addAccess(HttpServletResponse res, String token, Duration ttl) {
        add(res, ACCESS_COOKIE, token, ttl);
    }

    public void addRefresh(HttpServletResponse res, String token) {
        add(res, REFRESH_COOKIE, token, Duration.ofDays(props.getJwt().getRefreshTtlDays()));
    }

    public void clearAuth(HttpServletResponse res) {
        add(res, ACCESS_COOKIE, "", Duration.ZERO);
        add(res, REFRESH_COOKIE, "", Duration.ZERO);
    }

    public String get(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                return c.getValue();
            }
        }
        return null;
    }

    private void add(HttpServletResponse res, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.getCookie().isSecure())
                .sameSite(props.getCookie().getSameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
