package com.mindbridgex.assessment.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * One-shot messages that survive a redirect without server-side sessions (the app is stateless):
 * the message travels in a short-lived HttpOnly cookie and is rendered HTML-escaped by the layout.
 */
public final class Flash {

    public static final String COOKIE = "FLASH";

    public record Message(String level, String text) {
        public String cssClass() {
            return switch (level) {
                case "error" -> "danger";
                case "info" -> "info";
                default -> "success";
            };
        }
    }

    private Flash() {
    }

    public static void success(HttpServletResponse res, String text) {
        set(res, "success", text);
    }

    public static void error(HttpServletResponse res, String text) {
        set(res, "error", text);
    }

    public static void info(HttpServletResponse res, String text) {
        set(res, "info", text);
    }

    private static void set(HttpServletResponse res, String level, String text) {
        String clipped = text == null ? "" : (text.length() > 400 ? text.substring(0, 400) : text);
        String value = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((level + "|" + clipped).getBytes(StandardCharsets.UTF_8));
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, value)
                .httpOnly(true).sameSite("Strict").path("/").maxAge(Duration.ofSeconds(60)).build().toString());
    }

    public static Message decode(String cookieValue) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cookieValue), StandardCharsets.UTF_8);
            int i = raw.indexOf('|');
            if (i < 0) {
                return null;
            }
            return new Message(raw.substring(0, i), raw.substring(i + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void clear(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, "")
                .httpOnly(true).sameSite("Strict").path("/").maxAge(Duration.ZERO).build().toString());
    }
}
