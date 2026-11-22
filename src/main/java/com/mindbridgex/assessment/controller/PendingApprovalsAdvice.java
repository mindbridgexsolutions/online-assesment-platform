package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.security.AuthUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Gives every page the number of exams and questions waiting for approval, for the Super Admin's "Approvals" tab. */
@ControllerAdvice
public class PendingApprovalsAdvice {
    private final JdbcClient jdbc;

    public PendingApprovalsAdvice(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @ModelAttribute("pendingApprovals")
    public Long pendingApprovals(@AuthenticationPrincipal AuthUser user) {
        if (user == null || !user.isSuperAdmin()) {
            return 0L;
        }
        return jdbc.sql("select (select count(*) from exams where approval_status = 'PENDING') + (select count(*) from questions where approval_status = 'PENDING')").query(Long.class).single();
    }
}
