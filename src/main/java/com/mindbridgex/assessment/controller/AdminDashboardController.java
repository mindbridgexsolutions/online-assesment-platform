package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.domain.AttemptStatus;
import com.mindbridgex.assessment.domain.ExamStatus;
import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.repository.AttemptRepository;
import com.mindbridgex.assessment.repository.BatchRepository;
import com.mindbridgex.assessment.repository.ExamRepository;
import com.mindbridgex.assessment.repository.QuestionRepository;
import com.mindbridgex.assessment.repository.SubjectRepository;
import com.mindbridgex.assessment.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@PreAuthorize("hasRole(\'ADMIN\')")
public class AdminDashboardController {
    private final UserRepository users;
    private final BatchRepository batches;
    private final SubjectRepository subjects;
    private final QuestionRepository questions;
    private final ExamRepository exams;
    private final AttemptRepository attempts;

    @GetMapping("/admin")
    @Transactional(readOnly = true)
    public String dashboard(Model model) {
        model.addAttribute("students", users.countActiveByRole(RoleType.STUDENT));
        model.addAttribute("teachers", users.countActiveByRole(RoleType.TEACHER));
        model.addAttribute("admins", users.countActiveByRole(RoleType.ADMIN));
        model.addAttribute("batchCount", batches.count());
        model.addAttribute("subjectCount", subjects.count());
        model.addAttribute("questionCount", questions.count());
        model.addAttribute("publishedExams", exams.countByStatus(ExamStatus.PUBLISHED));
        model.addAttribute("draftExams", exams.countByStatus(ExamStatus.DRAFT));
        model.addAttribute("liveAttempts", attempts.countByStatus(AttemptStatus.IN_PROGRESS));
        model.addAttribute("pendingGrading", attempts.countByStatus(AttemptStatus.SUBMITTED));
        return "admin/dashboard";
    }

    public AdminDashboardController(final UserRepository users, final BatchRepository batches, final SubjectRepository subjects, final QuestionRepository questions, final ExamRepository exams, final AttemptRepository attempts) {
        this.users = users;
        this.batches = batches;
        this.subjects = subjects;
        this.questions = questions;
        this.exams = exams;
        this.attempts = attempts;
    }
}
