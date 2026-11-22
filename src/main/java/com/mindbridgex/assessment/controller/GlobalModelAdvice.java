package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.util.Fmt;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Model attributes available to every JSP page.
 */
@ControllerAdvice(basePackages = "com.mindbridgex.assessment.controller")
public class GlobalModelAdvice {
    private final Fmt fmt;

    @ModelAttribute("fmt")
    public Fmt fmt() {
        return fmt;
    }

    @ModelAttribute("currentUser")
    public AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthUser u ? u : null;
    }

    public GlobalModelAdvice(final Fmt fmt) {
        this.fmt = fmt;
    }
}
