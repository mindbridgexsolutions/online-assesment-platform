package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.service.PasswordResetRequestService;
import com.mindbridgex.assessment.util.RequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Public "Forgot password" page: asks the administrator to reset the password. */
@Controller
public class ForgotPasswordController {
    private final PasswordResetRequestService service;

    @GetMapping("/forgot-password")
    public String form() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String submit(@RequestParam(required = false) String identifier, HttpServletRequest request, Model model) {
        if (!service.request(identifier, RequestInfo.clientIp(request))) {
            model.addAttribute("error", "Too many requests from this device. Please try again after an hour or contact your administrator.");
            return "auth/forgot-password";
        }
        model.addAttribute("sent", true);
        return "auth/forgot-password";
    }

    public ForgotPasswordController(final PasswordResetRequestService service) {
        this.service = service;
    }
}
