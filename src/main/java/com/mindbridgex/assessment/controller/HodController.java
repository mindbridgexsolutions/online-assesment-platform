package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.entity.Exam;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.ExamRepository;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AccessService;
import com.mindbridgex.assessment.service.OverrideService;
import com.mindbridgex.assessment.util.Flash;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Set;

/** Head of Department: approve exams of the department before they can go live. */
@Controller
@RequestMapping("/hod")
public class HodController {
    private final ExamRepository exams;
    private final AccessService access;
    private final OverrideService overrides;

    public HodController(ExamRepository exams, AccessService access, OverrideService overrides) {
        this.exams = exams;
        this.access = access;
        this.overrides = overrides;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String page(@AuthenticationPrincipal AuthUser user, Model model) {
        Set<Long> subjectIds = access.teacherSubjectIds(user);
        List<Exam> list = exams.findAll().stream().filter(e -> user.isSuperAdmin() || subjectIds.contains(e.getSubject().getId()))
                .filter(e -> "PENDING".equals(e.getApprovalStatus()) || "REJECTED".equals(e.getApprovalStatus())).sorted((a, b) -> b.getId().compareTo(a.getId())).toList();
        model.addAttribute("exams", list);
        return "hod/approvals";
    }

    @PostMapping("/exams/{id}")
    public String decide(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestParam boolean approve, @RequestParam(required = false) String reason, HttpServletResponse res) {
        try {
            overrides.hodDecision(user, id, approve, reason);
            Flash.success(res, approve ? "Exam approved. The faculty can now publish it." : "Exam sent back as rejected.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/hod";
    }
}
