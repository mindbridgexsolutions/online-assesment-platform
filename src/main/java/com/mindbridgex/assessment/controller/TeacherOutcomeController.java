package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AccessService;
import com.mindbridgex.assessment.service.CoPoService;
import com.mindbridgex.assessment.util.Flash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** Course Outcomes of a subject and their mapping to Programme Outcomes. */
@Controller
@RequestMapping("/teacher/outcomes")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherOutcomeController {
    private final CoPoService copo;
    private final AccessService access;

    public TeacherOutcomeController(CoPoService copo, AccessService access) {
        this.copo = copo;
        this.access = access;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) Long subjectId, Model model) {
        model.addAttribute("subjects", access.teacherSubjects(user));
        model.addAttribute("subjectId", subjectId);
        if (subjectId != null) {
            model.addAttribute("cos", copo.cosForSubject(user, subjectId));
            model.addAttribute("pos", copo.posForSubject(subjectId));
            model.addAttribute("map", copo.mapping(subjectId));
        }
        return "teacher/outcomes";
    }

    @PostMapping("/co")
    public String addCo(@AuthenticationPrincipal AuthUser user, @RequestParam Long subjectId, @RequestParam String code, @RequestParam(required = false) String description,
                        @RequestParam(required = false) BigDecimal target, HttpServletResponse res) {
        try {
            copo.addCo(user, subjectId, code, description, target);
            Flash.success(res, "Course Outcome added.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/teacher/outcomes?subjectId=" + subjectId;
    }

    @PostMapping("/co/{id}/delete")
    public String deleteCo(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestParam Long subjectId, HttpServletResponse res) {
        try {
            copo.deleteCo(user, id);
            Flash.success(res, "Course Outcome removed. Questions tagged with it are now untagged.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/teacher/outcomes?subjectId=" + subjectId;
    }

    @PostMapping("/map")
    public String saveMap(@AuthenticationPrincipal AuthUser user, @RequestParam Long subjectId, HttpServletRequest req, HttpServletResponse res) {
        Map<String, String> params = new HashMap<>();
        req.getParameterMap().forEach((k, v) -> {
            if (k.startsWith("m_") && v.length > 0) {
                params.put(k, v[0]);
            }
        });
        try {
            copo.saveMapping(user, subjectId, params);
            Flash.success(res, "CO-PO mapping saved.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/teacher/outcomes?subjectId=" + subjectId;
    }
}
