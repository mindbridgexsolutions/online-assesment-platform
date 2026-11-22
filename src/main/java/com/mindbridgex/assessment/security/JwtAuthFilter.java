package com.mindbridgex.assessment.security;

import com.mindbridgex.assessment.service.AuthService;
import com.mindbridgex.assessment.util.RequestInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Authenticates each request from the JWT access token (HttpOnly cookie, or "Authorization: Bearer" header).
 * If the access token is missing/expired but a valid refresh cookie is present, the session is transparently
 * renewed (refresh token rotation) so long exams are never interrupted by a token expiry.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Set<String> PASSWORD_CHANGE_ALLOWED = Set.of("/auth/change-password", "/auth/logout", "/error");

    private final JwtService jwt;
    private final AuthService authService;
    private final UserLookupService lookup;
    private final CookieUtil cookies;

    public JwtAuthFilter(JwtService jwt, AuthService authService, UserLookupService lookup, CookieUtil cookies) {
        this.jwt = jwt;
        this.authService = authService;
        this.lookup = lookup;
        this.cookies = cookies;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = pathOf(request);
        return p.startsWith("/webjars/") || p.startsWith("/css/") || p.startsWith("/js/") || p.startsWith("/img/")
                || p.equals("/favicon.ico") || p.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        AuthUser user = fromAccessToken(request);
        if (user == null) {
            String refresh = cookies.get(request, CookieUtil.REFRESH_COOKIE);
            if (refresh != null && !pathOf(request).equals("/auth/login")) {
                Optional<AuthService.IssuedTokens> renewed = authService.refresh(refresh, request);
                if (renewed.isPresent()) {
                    AuthService.IssuedTokens t = renewed.get();
                    cookies.addAccess(response, t.accessToken(), jwt.accessTtl());
                    if (t.refreshToken() != null) {
                        cookies.addRefresh(response, t.refreshToken());
                    }
                    user = t.principal();
                } else {
                    cookies.clearAuth(response);
                }
            }
        }

        if (user != null) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            if (user.isMustChangePassword() && !PASSWORD_CHANGE_ALLOWED.contains(pathOf(request))) {
                if (RequestInfo.isApi(request)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Password change required");
                } else {
                    response.sendRedirect(request.getContextPath() + "/auth/change-password");
                }
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private AuthUser fromAccessToken(HttpServletRequest request) {
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = header.substring(7).trim();
        }
        if (token == null) {
            token = cookies.get(request, CookieUtil.ACCESS_COOKIE);
        }
        if (token == null) {
            return null;
        }
        return jwt.parseAccess(token)
                .map(c -> {
                    AuthSnapshot s = lookup.snapshot(c.userId());
                    if (s == null || !s.active() || s.tokenVersion() != c.tokenVersion()) {
                        return null;
                    }
                    return s.toPrincipal();
                })
                .orElse(null);
    }

    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }
}
