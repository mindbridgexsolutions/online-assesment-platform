package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.domain.Difficulty;
import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.domain.ResultPolicy;
import com.mindbridgex.assessment.dto.ExamForm;
import com.mindbridgex.assessment.entity.Exam;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.BatchRepository;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AccessService;
import com.mindbridgex.assessment.service.CategoryService;
import com.mindbridgex.assessment.service.ExamService;
import com.mindbridgex.assessment.util.Flash;
import com.mindbridgex.assessment.util.FormErrors;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/teacher/exams")
@PreAuthorize("hasRole(\'TEACHER\')")
public class TeacherExamController {
    private final ExamService examService;
    private final AccessService access;
    private final CategoryService categoryService;
    private final BatchRepository batches;
    @org.springframework.beans.factory.annotation.Autowired
    private com.mindbridgex.assessment.service.CoPoService coPo;

    @GetMapping
    public String list(@AuthenticationPrincipal AuthUser user, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", examService.list(user, page, 15));
        return "teacher/exams";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal AuthUser user, Model model) {
        ExamForm f = new ExamForm();
        java.time.LocalDateTime t = java.time.LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0);
        f.setStartAt(t);
        f.setEndAt(t.plusHours(3));
        f.setNegativeMarking(true);                              // default: negative marking on ...
        f.setNegativeFraction(new java.math.BigDecimal("0.33")); // ... one third of the marks, i.e. -0.5 on a 1.5-mark question; the teacher can change it
        prepare(user, f, model);
        return "teacher/exam-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, Model model) {
        prepare(user, examService.toForm(user, id), model);
        model.addAttribute("locked", examService.detail(user, id).isStructureLocked());
        return "teacher/exam-form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AuthUser user, @Valid @ModelAttribute("form") ExamForm form, BindingResult br, Model model, HttpServletResponse res) {
        return save(user, null, form, br, model, res);
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @ModelAttribute("form") ExamForm form, BindingResult br, Model model, HttpServletResponse res) {
        return save(user, id, form, br, model, res);
    }

    private String save(AuthUser user, Long id, ExamForm form, BindingResult br, Model model, HttpServletResponse res) {
        form.setId(id);
        List<String> errors = new ArrayList<>(FormErrors.of(br));
        if (errors.isEmpty()) {
            try {
                Exam e = examService.save(user, form);
                Flash.success(res, "Exam saved.");
                return "redirect:/teacher/exams/" + e.getId();
            } catch (AppException e) {
                if (e.getStatus().value() == 403) {
                    throw e;
                }
                errors.add(e.getMessage());
            }
        }
        model.addAttribute("errors", errors);
        prepare(user, form, model);
        if (id != null) {
            model.addAttribute("locked", examService.detail(user, id).isStructureLocked());
        }
        return "teacher/exam-form";
    }

    private void prepare(AuthUser user, ExamForm f, Model model) {
        model.addAttribute("form", f);
        model.addAttribute("subjects", access.teacherSubjects(user));
        model.addAttribute("policies", ResultPolicy.values());
        model.addAttribute("examTypes", com.mindbridgex.assessment.domain.ExamType.values());
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, Model model) {
        ExamService.ExamDetail d = examService.detail(user, id);
        model.addAttribute("d", d);
        model.addAttribute("categories", categoryService.forSubject(user, d.exam().getSubject().getId()));
        model.addAttribute("difficulties", Difficulty.values());
        model.addAttribute("coCoverage", coPo.coverage(id, false));
        model.addAttribute("bloomCoverage", coPo.coverage(id, true));
        model.addAttribute("batches", batches.findByActiveTrueOrderByNameAsc());
        return "teacher/exam-detail";
    }

    @GetMapping("/{id}/pick")
    public String pick(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) QuestionType type, @RequestParam(required = false) Difficulty difficulty, @RequestParam(required = false) String q, Model model) {
        Exam e = examService.get(user, id);
        model.addAttribute("exam", e);
        model.addAttribute("questions", examService.pickable(user, id, categoryId, type, difficulty, q));
        model.addAttribute("categories", categoryService.forSubject(user, e.getSubject().getId()));
        model.addAttribute("types", QuestionType.values());
        model.addAttribute("difficulties", Difficulty.values());
        model.addAttribute("fCategory", categoryId);
        model.addAttribute("fType", type);
        model.addAttribute("fDifficulty", difficulty);
        model.addAttribute("fQ", q);
        return "teacher/exam-pick";
    }

    @PostMapping("/{id}/questions")
    public String addQuestions(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestParam(required = false) List<Long> questionIds, HttpServletResponse res) {
        if (questionIds == null || questionIds.isEmpty()) {
            Flash.error(res, "Select at least one question.");
            return "redirect:/teacher/exams/" + id + "/pick";
        }
        return run(id, res, "Added " + questionIds.size() + " question(s).", () -> examService.addQuestions(user, id, questionIds));
    }

    @PostMapping("/{id}/questions/{eqId}/marks")
    public String marks(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable Long eqId, @RequestParam BigDecimal marks, HttpServletResponse res) {
        return run(id, res, "Marks updated.", () -> examService.updateMarks(user, id, eqId, marks));
    }

    @PostMapping("/{id}/questions/{eqId}/remove")
    public String removeQuestion(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable Long eqId, HttpServletResponse res) {
        return run(id, res, "Question removed.", () -> examService.removeQuestion(user, id, eqId));
    }

    @PostMapping("/{id}/rules")
    public String addRule(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Difficulty difficulty, @RequestParam int count, @RequestParam BigDecimal marks, HttpServletResponse res) {
        return run(id, res, "Random-selection rule added.", () -> examService.addRule(user, id, categoryId, difficulty, count, marks));
    }

    @PostMapping("/{id}/rules/{ruleId}/remove")
    public String removeRule(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable Long ruleId, HttpServletResponse res) {
        return run(id, res, "Rule removed.", () -> examService.removeRule(user, id, ruleId));
    }

    @PostMapping("/{id}/assign/batches")
    public String assignBatches(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestParam(required = false) List<Long> batchIds, HttpServletResponse res) {
        if (batchIds == null || batchIds.isEmpty()) {
            Flash.error(res, "Select at least one batch.");
            return "redirect:/teacher/exams/" + id;
        }
        return run(id, res, "Batches assigned.", () -> examService.assignBatches(user, id, batchIds));
    }

    @PostMapping("/{id}/assign/students")
    public String assignStudents(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestParam String students, HttpServletResponse res) {
        try {
            List<String> unknown = examService.assignStudents(user, id, students);
            if (unknown.isEmpty()) {
                Flash.success(res, "Students assigned.");
            } else {
                Flash.error(res, "Assigned, but these usernames are not active students: " + String.join(", ", unknown));
            }
        } catch (AppException e) {
            rethrowForbidden(e);
            Flash.error(res, e.getMessage());
        }
        return "redirect:/teacher/exams/" + id;
    }

    @PostMapping("/{id}/assign/{assignmentId}/remove")
    public String removeAssignment(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable Long assignmentId, HttpServletResponse res) {
        return run(id, res, "Assignment removed.", () -> examService.removeAssignment(user, id, assignmentId));
    }

    @PostMapping("/{id}/request-approval")
    public String requestApproval(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, HttpServletResponse res) {
        try {
            examService.requestApproval(user, id);
            Flash.success(res, "Sent to the Head of Department for approval.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/teacher/exams/" + id;
    }

    @PostMapping("/{id}/publish")
    public String publish(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, HttpServletResponse res) {
        return run(id, res, "Exam published. Assigned students can now see it.", () -> examService.publish(user, id));
    }

    @PostMapping("/{id}/unpublish")
    public String unpublish(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, HttpServletResponse res) {
        return run(id, res, "Exam moved back to draft.", () -> examService.unpublish(user, id));
    }

    @PostMapping("/{id}/archive")
    public String archive(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, HttpServletResponse res) {
        return run(id, res, "Exam archived.", () -> examService.archive(user, id));
    }

    @PostMapping("/{id}/release")
    public String release(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestParam boolean released, @RequestParam(required = false) String back, HttpServletResponse res) {
        String page = run(id, res, released ? "Results released to students." : "Results hidden from students.", () -> examService.setResultsReleased(user, id, released));
        if ("list".equals(back)) {
            return "redirect:/teacher/exams";
        }
        return "report".equals(back) ? "redirect:/reports/exams/" + id : page;
    }

    private String run(Long examId, HttpServletResponse res, String ok, Runnable action) {
        try {
            action.run();
            Flash.success(res, ok);
        } catch (AppException e) {
            rethrowForbidden(e);
            Flash.error(res, e.getMessage());
        }
        return "redirect:/teacher/exams/" + examId;
    }

    private static void rethrowForbidden(AppException e) {
        if (e.getStatus().value() == 403 || e.getStatus().value() == 404) {
            throw e;
        }
    }

    public TeacherExamController(final ExamService examService, final AccessService access, final CategoryService categoryService, final BatchRepository batches) {
        this.examService = examService;
        this.access = access;
        this.categoryService = categoryService;
        this.batches = batches;
    }
}
