package com.mindbridgex.assessment.config;

import com.mindbridgex.assessment.security.CsrfCookieFilter;
import com.mindbridgex.assessment.security.JwtAuthFilter;
import com.mindbridgex.assessment.util.RequestInfo;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** The JWT filter is wired inside the security chain only; stop Boot from also registering it globally. */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter, AppProperties props)
            throws Exception {

        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(c -> c.secure(props.getCookie().isSecure()).sameSite("Strict").path("/"));

        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // The app is stateless: without this, every authenticated request would rotate the CSRF token
                        // (the default strategy treats each JWT-authenticated request as a fresh login).
                        .sessionAuthenticationStrategy(new org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy()))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .requestCache(rc -> rc.disable())
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=()"))
                        .addHeaderWriter(new StaticHeadersWriter("X-Content-Type-Options", "nosniff")))
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR, DispatcherType.INCLUDE)
                        .permitAll()
                        .requestMatchers("/login", "/auth/login", "/auth/refresh", "/forgot-password", "/error",
                                "/webjars/**", "/css/**", "/js/**", "/img/**", "/favicon.ico",
                                "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/super/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/teacher/**").hasRole("TEACHER")
                        .requestMatchers("/student/**", "/api/attempts/**").hasRole("STUDENT")
                        .requestMatchers("/hod/**").hasAnyRole("HOD", "SUPER_ADMIN")
                        .requestMatchers("/reports/**").hasAnyRole("TEACHER", "ADMIN", "IQAC")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> {
            if (RequestInfo.isApi(request)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"unauthenticated\",\"message\":\"Please sign in again.\"}");
                return;
            }
            String target = request.getContextPath() + "/login";
            if ("GET".equalsIgnoreCase(request.getMethod())) {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                if (!path.isEmpty() && !"/".equals(path) && !path.startsWith("/login")) {
                    String next = request.getQueryString() == null ? path : path + "?" + request.getQueryString();
                    target += "?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8);
                }
            }
            response.sendRedirect(target);
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            if (RequestInfo.isApi(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"forbidden\",\"message\":\"You are not allowed to do that.\"}");
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        };
    }
}
