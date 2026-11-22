package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.security.CookieUtil;
import com.mindbridgex.assessment.security.JwtService;
import com.mindbridgex.assessment.service.AuthService;
import com.mindbridgex.assessment.service.UserService;
import com.mindbridgex.assessment.util.Flash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.Map;
import java.util.Optional;

@Controller
public class AuthController {
    private final AuthService authService;
    private final UserService userService;
    private final JwtService jwt;
    private final CookieUtil cookies;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AuthUser user) {
        return "redirect:" + user.homePath();
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String next, @AuthenticationPrincipal AuthUser user, Model model) {
        if (user != null) {
            return "redirect:" + user.homePath();
        }
        model.addAttribute("next", safeNext(next));
        return "auth/login";
    }

    @PostMapping("/auth/login")
    public String login(@RequestParam String username, @RequestParam String password, @RequestParam(required = false) String next, HttpServletRequest request, HttpServletResponse response, Model model) {
        try {
            AuthService.IssuedTokens tokens = authService.login(username, password, request);
            cookies.addAccess(response, tokens.accessToken(), jwt.accessTtl());
            cookies.addRefresh(response, tokens.refreshToken());
            AuthUser principal = tokens.principal();
            if (principal.isMustChangePassword()) {
                return "redirect:/auth/change-password";
            }
            String target = safeNext(next);
            return "redirect:" + (target.isEmpty() ? principal.homePath() : target);
        } catch (AppException e) {
            response.setStatus(e.getStatus().value());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("username", username);
            model.addAttribute("next", safeNext(next));
            return "auth/login";
        }
    }

    @PostMapping("/auth/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(cookies.get(request, CookieUtil.REFRESH_COOKIE));
        cookies.clearAuth(response);
        Flash.info(response, "You have been signed out.");
        return "redirect:/login";
    }

    @GetMapping("/auth/change-password")
    public String changePasswordPage() {
        return "auth/change-password";
    }

    @PostMapping("/auth/change-password")
    public String changePassword(@AuthenticationPrincipal AuthUser user, @RequestParam String currentPassword, @RequestParam String newPassword, @RequestParam String confirmPassword, HttpServletResponse response, Model model) {
        try {
            userService.changePassword(user.getId(), currentPassword, newPassword, confirmPassword);
        } catch (AppException e) {
            response.setStatus(e.getStatus().value());
            model.addAttribute("error", e.getMessage());
            return "auth/change-password";
        }
        cookies.clearAuth(response);
        Flash.success(response, "Password changed. Please sign in with your new password.");
        return "redirect:/login";
    }

    /**
     * Explicit token rotation endpoint for AJAX clients (browsers are also renewed transparently by the filter).
     */
    @PostMapping("/auth/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refresh(HttpServletRequest request, HttpServletResponse response) {
        Optional<AuthService.IssuedTokens> renewed = authService.refresh(cookies.get(request, CookieUtil.REFRESH_COOKIE), request);
        if (renewed.isEmpty()) {
            cookies.clearAuth(response);
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        AuthService.IssuedTokens t = renewed.get();
        cookies.addAccess(response, t.accessToken(), jwt.accessTtl());
        if (t.refreshToken() != null) {
            cookies.addRefresh(response, t.refreshToken());
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * Only same-site relative paths are accepted as post-login targets (prevents open redirects).
     */
    static String safeNext(String next) {
        if (next == null || next.isBlank()) {
            return "";
        }
        String n = next.trim();
        if (!n.startsWith("/") || n.startsWith("//") || n.contains("\\") || n.contains("\r") || n.contains("\n") || n.startsWith("/login") || n.startsWith("/auth/")) {
            return "";
        }
        return n;
    }

    public AuthController(final AuthService authService, final UserService userService, final JwtService jwt, final CookieUtil cookies) {
        this.authService = authService;
        this.userService = userService;
        this.jwt = jwt;
        this.cookies = cookies;
    }
}
