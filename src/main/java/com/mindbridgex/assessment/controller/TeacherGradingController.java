package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.entity.Attempt;
import com.mindbridgex.assessment.entity.AttemptAnswer;
import com.mindbridgex.assessment.entity.AttemptQuestion;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.*;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AccessService;
import com.mindbridgex.assessment.service.GradingService;
import com.mindbridgex.assessment.util.Flash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teacher/grading")
@PreAuthorize("hasRole(\'TEACHER\')")
public class TeacherGradingController {

    public record EssayRow(AttemptQuestion question, AttemptAnswer answer) {
    }

    private final AttemptRepository attempts;
    private final AttemptQuestionRepository attemptQuestions;
    private final AttemptAnswerRepository answers;
    private final ExamRepository exams;
    private final UserRepository users;
    private final AccessService access;
    private final GradingService grading;

    @GetMapping
    public String pending(@AuthenticationPrincipal AuthUser user, Model model) {
        model.addAttribute("rows", attempts.findPendingGradingForTeacher(user.getId()));
        return "teacher/grading";
    }

    @GetMapping("/{attemptId}")
    public String grade(@AuthenticationPrincipal AuthUser user, @PathVariable Long attemptId, Model model) {
        Attempt a = authorised(user, attemptId);
        List<AttemptQuestion> qs = attemptQuestions.findByAttemptIdOrderByPositionAsc(attemptId);
        Map<Long, AttemptAnswer> byQ = answers.findByAttemptId(attemptId).stream().collect(Collectors.toMap(AttemptAnswer::getAttemptQuestionId, x -> x));
        List<EssayRow> essays = qs.stream().filter(q -> q.getType() == com.mindbridgex.assessment.domain.QuestionType.ESSAY).map(q -> new EssayRow(q, byQ.get(q.getId()))).toList();
        model.addAttribute("attempt", a);
        model.addAttribute("exam", exams.findById(a.getExamId()).orElseThrow());
        model.addAttribute("student", users.findById(a.getStudentId()).orElseThrow());
        model.addAttribute("essays", essays);
        return "teacher/grade-attempt";
    }

    @PostMapping("/{attemptId}")
    public String save(@AuthenticationPrincipal AuthUser user, @PathVariable Long attemptId, HttpServletRequest request, HttpServletResponse res) {
        authorised(user, attemptId);
        Map<Long, BigDecimal> marks = new HashMap<>();
        Map<Long, String> feedback = new HashMap<>();
        for (AttemptQuestion q : attemptQuestions.findByAttemptIdOrderByPositionAsc(attemptId)) {
            String m = request.getParameter("marks_" + q.getId());
            if (m != null && !m.isBlank()) {
                try {
                    marks.put(q.getId(), new BigDecimal(m.trim()));
                } catch (NumberFormatException e) {
                    Flash.error(res, "Marks must be numbers.");
                    return "redirect:/teacher/grading/" + attemptId;
                }
            }
            feedback.put(q.getId(), request.getParameter("feedback_" + q.getId()));
        }
        try {
            grading.gradeManually(attemptId, marks, feedback, user.getId());
            Flash.success(res, "Grades saved.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
            return "redirect:/teacher/grading/" + attemptId;
        }
        return "redirect:/teacher/grading";
    }

    private Attempt authorised(AuthUser user, Long attemptId) {
        Attempt a = attempts.findById(attemptId).orElseThrow(() -> AppException.notFound("Attempt"));
        access.requireSubject(user, exams.findById(a.getExamId()).orElseThrow().getSubject().getId());
        return a;
    }

    public TeacherGradingController(final AttemptRepository attempts, final AttemptQuestionRepository attemptQuestions, final AttemptAnswerRepository answers, final ExamRepository exams, final UserRepository users, final AccessService access, final GradingService grading) {
        this.attempts = attempts;
        this.attemptQuestions = attemptQuestions;
        this.answers = answers;
        this.exams = exams;
        this.users = users;
        this.access = access;
        this.grading = grading;
    }
}
