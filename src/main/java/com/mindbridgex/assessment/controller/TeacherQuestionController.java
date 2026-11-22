package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.domain.Difficulty;
import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.dto.OptionForm;
import com.mindbridgex.assessment.dto.QuestionForm;
import com.mindbridgex.assessment.entity.Question;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AccessService;
import com.mindbridgex.assessment.service.CategoryService;
import com.mindbridgex.assessment.service.QuestionService;
import com.mindbridgex.assessment.util.Flash;
import com.mindbridgex.assessment.util.FormErrors;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/teacher/questions")
@PreAuthorize("hasRole(\'TEACHER\')")
public class TeacherQuestionController {
    private static final int PAGE_SIZE = 20;
    private final QuestionService questionService;
    private final CategoryService categoryService;
    private final AccessService access;
    private final com.mindbridgex.assessment.service.CoPoService coPo;
    private final com.mindbridgex.assessment.service.ExamUploadService uploads;

    @GetMapping
    public String list(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) Long subjectId, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) QuestionType type, @RequestParam(required = false) Difficulty difficulty, @RequestParam(required = false) String tag, @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Long highlightPaper, @RequestParam(required = false) Long highlightQuestion, Model model) {
        Page<QuestionService.QuestionRow> result = questionService.search(user, subjectId, categoryId, type, difficulty, tag, q, page, PAGE_SIZE);
        model.addAttribute("page", result);
        model.addAttribute("subjects", access.teacherSubjects(user));
        model.addAttribute("categories", subjectId == null ? List.of() : categoryService.forSubject(user, subjectId));
        model.addAttribute("types", QuestionType.values());
        model.addAttribute("difficulties", Difficulty.values());
        model.addAttribute("fSubject", subjectId);
        model.addAttribute("fCategory", categoryId);
        model.addAttribute("fType", type);
        model.addAttribute("fDifficulty", difficulty);
        model.addAttribute("fTag", tag);
        model.addAttribute("fQ", q);
        model.addAttribute("hp", highlightPaper);
        if (highlightQuestion != null) {
            try {
                model.addAttribute("hq", questionService.row(user, highlightQuestion));
            } catch (RuntimeException e) {
                // a question that is gone or not yours is simply not shown
            }
        }
        model.addAttribute("drafts", uploads.papers(user).stream().filter(d -> d.examId() == null).toList());
        return "teacher/questions";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) Long subjectId, Model model) {
        QuestionForm f = new QuestionForm();
        f.setSubjectId(subjectId);
        prepare(user, f, model);
        return "teacher/question-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, Model model) {
        prepare(user, questionService.toForm(user, id), model);
        return "teacher/question-form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AuthUser user, @Valid @ModelAttribute("form") QuestionForm form, BindingResult br, Model model, HttpServletResponse res) {
        return save(user, null, form, br, model, res);
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @ModelAttribute("form") QuestionForm form, BindingResult br, Model model, HttpServletResponse res) {
        return save(user, id, form, br, model, res);
    }

    private String save(AuthUser user, Long id, QuestionForm form, BindingResult br, Model model, HttpServletResponse res) {
        form.setId(id);
        List<String> errors = new ArrayList<>(FormErrors.of(br));
        if (id != null && form.getExistingImage() == null) {
            form.setExistingImage(questionService.get(user, id).getImagePath());
        }
        if (errors.isEmpty()) {
            errors.addAll(questionService.validate(form));
        }
        if (errors.isEmpty()) {
            try {
                questionService.save(user, form);
                Flash.success(res, "Question saved.");
                return "redirect:/teacher/questions";
            } catch (AppException e) {
                errors.add(e.getMessage());
            }
        }
        if (id != null) {
            form.setExistingImage(questionService.get(user, id).getImagePath());
        }
        model.addAttribute("errors", errors);
        prepare(user, form, model);
        return "teacher/question-form";
    }

    @PostMapping("/{id}/archive")
    public String archive(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, HttpServletResponse res) {
        questionService.archive(user, id);
        Flash.success(res, "Question archived. Existing exam attempts are not affected.");
        return "redirect:/teacher/questions";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, HttpServletResponse res) {
        try {
            questionService.delete(user, id);
            Flash.success(res, "Question deleted.");
        } catch (com.mindbridgex.assessment.exception.AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/teacher/questions";
    }

    private void prepare(AuthUser user, QuestionForm f, Model model) {
        if (f.getOptions().isEmpty()) {
            for (int i = 0; i < 4; i++) {
                f.getOptions().add(new OptionForm());
            }
        }
        model.addAttribute("form", f);
        model.addAttribute("subjects", access.teacherSubjects(user));
        model.addAttribute("categories", f.getSubjectId() == null ? List.of() : categoryService.forSubject(user, f.getSubjectId()));
        model.addAttribute("types", QuestionType.values());
        model.addAttribute("difficulties", Difficulty.values());
        model.addAttribute("blooms", com.mindbridgex.assessment.domain.Bloom.values());
        model.addAttribute("cos", coPo.cosForUser(user));
    }

    /**
     * JSON-free helper for the editor: categories of a subject as simple option list.
     */
    @GetMapping(value = "/categories", produces = "application/json")
    @ResponseBody
    public List<java.util.Map<String, Object>> categories(@AuthenticationPrincipal AuthUser user, @RequestParam Long subjectId) {
        return categoryService.forSubject(user, subjectId).stream().map(c -> java.util.Map.<String, Object>of("id", c.getId(), "name", c.getName())).toList();
    }

    public TeacherQuestionController(final QuestionService questionService, final CategoryService categoryService, final AccessService access, final com.mindbridgex.assessment.service.CoPoService coPo, final com.mindbridgex.assessment.service.ExamUploadService uploads) {
        this.uploads = uploads;
        this.questionService = questionService;
        this.categoryService = categoryService;
        this.access = access;
        this.coPo = coPo;
    }
}
