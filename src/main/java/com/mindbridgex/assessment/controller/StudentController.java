package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.entity.Attempt;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AttemptService;
import com.mindbridgex.assessment.service.StudentService;
import com.mindbridgex.assessment.util.Flash;
import com.mindbridgex.assessment.util.RequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/student")
@PreAuthorize("hasRole(\'STUDENT\')")
public class StudentController {
    private final StudentService studentService;
    private final AttemptService attemptService;
    private final JdbcClient jdbc;

    @GetMapping
    public String dashboard(@AuthenticationPrincipal AuthUser user, Model model) {
        model.addAttribute("cards", studentService.dashboard(user));
        return "student/dashboard";
    }

    @GetMapping("/exams/{id}")
    public String instructions(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, Model model) {
        StudentService.ExamCard card = studentService.card(user, id);
        model.addAttribute("card", card);
        // Facts for the standard instructions: number of questions and the marks per question when they are all the same.
        jdbc.sql("select count(*) n, count(distinct marks) d, min(marks) m from exam_questions where exam_id = :e").param("e", id).query((rs, i) -> {
            if (rs.getInt("n") > 0) {
                model.addAttribute("questionCount", rs.getInt("n"));
                if (rs.getInt("d") == 1) {
                    model.addAttribute("marksEach", rs.getBigDecimal("m").stripTrailingZeros().toPlainString());
                    model.addAttribute("negativeEach", rs.getBigDecimal("m").multiply(card.exam().getNegativeFraction()).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
                }
            }
            return 0;
        }).single();
        // The teacher's own text is shown too, but lines that repeat a standard instruction are left out.
        String custom = card.exam().getInstructions();
        if (custom != null && !custom.isBlank()) {
            java.util.List<String> keep = new java.util.ArrayList<>();
            for (String line : custom.split("\\R")) {
                if (!line.isBlank() && !line.toLowerCase(java.util.Locale.ROOT).matches(".*(negative mark|wrong answer|incorrect answer|unanswered|internal choice|compulsory|each question carries|calculator|mark only one).*")) {
                    keep.add(line.trim());
                }
            }
            model.addAttribute("customInstructions", keep);
        }
        return "student/instructions";
    }

    @PostMapping("/exams/{id}/start")
    public String start(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, HttpServletRequest request, HttpServletResponse res) {
        try {
            Attempt a = attemptService.startOrResume(user, id, RequestInfo.clientIp(request), RequestInfo.userAgent(request));
            return "redirect:/student/attempts/" + a.getId();
        } catch (AppException e) {
            if (e.getStatus().value() == 404) {
                throw e;
            }
            Flash.error(res, e.getMessage());
            return "redirect:/student/exams/" + id;
        }
    }

    @GetMapping("/attempts/{id}")
    public String player(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, Model model, HttpServletResponse res) {
        try {
            AttemptService.PlayerView v = attemptService.openPlayer(user, id);
            res.setHeader("Cache-Control", "no-store");
            model.addAttribute("v", v);
            return "student/player";
        } catch (AppException e) {
            if (e.getStatus().value() == 410) {
                Flash.info(res, e.getMessage());
                return "redirect:/student/results";
            }
            throw e;
        }
    }

    @GetMapping("/results")
    public String results(@AuthenticationPrincipal AuthUser user, Model model) {
        model.addAttribute("rows", studentService.results(user));
        return "student/results";
    }

    @GetMapping("/results/{attemptId}")
    public String review(@AuthenticationPrincipal AuthUser user, @PathVariable Long attemptId, Model model, HttpServletResponse res) {
        try {
            model.addAttribute("r", studentService.review(user, attemptId));
            return "student/review";
        } catch (AppException e) {
            if (e.getStatus().value() == 403 || e.getStatus().value() == 409) {
                Flash.info(res, e.getMessage());
                return "redirect:/student/results";
            }
            throw e;
        }
    }

    public StudentController(final StudentService studentService, final AttemptService attemptService, final JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.studentService = studentService;
        this.attemptService = attemptService;
    }
}
