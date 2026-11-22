package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.domain.ExamStatus;
import com.mindbridgex.assessment.repository.AttemptRepository;
import com.mindbridgex.assessment.repository.ExamRepository;
import com.mindbridgex.assessment.repository.QuestionRepository;
import com.mindbridgex.assessment.repository.QuestionSpecs;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Set;

@Controller
@PreAuthorize("hasRole(\'TEACHER\')")
public class TeacherDashboardController {
    private final AccessService access;
    private final QuestionRepository questions;
    private final ExamRepository exams;
    private final AttemptRepository attempts;

    @GetMapping("/teacher")
    public String dashboard(@AuthenticationPrincipal AuthUser user, Model model) {
        Set<Long> ids = access.teacherSubjectIds(user);
        model.addAttribute("subjects", access.teacherSubjects(user));
        model.addAttribute("questionCount", ids.isEmpty() ? 0 : questions.count(QuestionSpecs.search(ids, null, null, null, null, null, null)));
        model.addAttribute("publishedExams", ids.isEmpty() ? 0 : exams.countBySubjectIdInAndStatus(ids, ExamStatus.PUBLISHED));
        model.addAttribute("draftExams", ids.isEmpty() ? 0 : exams.countBySubjectIdInAndStatus(ids, ExamStatus.DRAFT));
        model.addAttribute("pendingGrading", attempts.countPendingGradingForTeacher(user.getId()));
        return "teacher/dashboard";
    }

    public TeacherDashboardController(final AccessService access, final QuestionRepository questions, final ExamRepository exams, final AttemptRepository attempts) {
        this.access = access;
        this.questions = questions;
        this.exams = exams;
        this.attempts = attempts;
    }
}
