package com.mindbridgex.assessment.config;

import com.mindbridgex.assessment.security.AuthUser;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableScheduling
@EnableCaching
public class AppConfig {

    /** Fills created_by with the id of the signed-in user (empty for seeders / scheduler jobs). */
    @Bean
    public AuditorAware<Long> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
                return Optional.of(user.getId());
            }
            return Optional.empty();
        };
    }

    @org.springframework.context.annotation.Bean
    public java.time.Clock clock() {
        return java.time.Clock.systemUTC();
    }
}
