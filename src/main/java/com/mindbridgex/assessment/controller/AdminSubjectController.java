package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.dto.SubjectForm;
import com.mindbridgex.assessment.entity.Subject;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.service.SubjectService;
import com.mindbridgex.assessment.service.UserService;
import com.mindbridgex.assessment.util.Flash;
import com.mindbridgex.assessment.util.FormErrors;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@Controller
@RequestMapping("/admin/subjects")
@PreAuthorize("hasRole(\'ADMIN\')")
public class AdminSubjectController {
    private final SubjectService subjectService;
    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("subjects", subjectService.list());
        return "admin/subjects";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new SubjectForm());
        model.addAttribute("teachers", userService.activeByRole(RoleType.TEACHER));
        return "admin/subject-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", subjectService.toForm(id));
        model.addAttribute("teachers", userService.activeByRole(RoleType.TEACHER));
        return "admin/subject-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") SubjectForm form, BindingResult br, Model model, HttpServletResponse response) {
        return save(null, form, br, model, response);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") SubjectForm form, BindingResult br, Model model, HttpServletResponse response) {
        return save(id, form, br, model, response);
    }

    private String save(Long id, SubjectForm form, BindingResult br, Model model, HttpServletResponse response) {
        form.setId(id);
        model.addAttribute("teachers", userService.activeByRole(RoleType.TEACHER));
        if (br.hasErrors()) {
            model.addAttribute("errors", FormErrors.of(br));
            return "admin/subject-form";
        }
        try {
            Subject s = subjectService.save(form);
            Flash.success(response, "Subject \'" + s.getName() + "\' saved.");
            return "redirect:/admin/subjects";
        } catch (AppException e) {
            model.addAttribute("errors", List.of(e.getMessage()));
            return "admin/subject-form";
        }
    }

    @PostMapping("/{id}/toggle-active")
    public String toggle(@PathVariable Long id, @RequestParam boolean active, HttpServletResponse response) {
        subjectService.setActive(id, active);
        Flash.success(response, active ? "Subject activated." : "Subject deactivated.");
        return "redirect:/admin/subjects";
    }

    public AdminSubjectController(final SubjectService subjectService, final UserService userService) {
        this.subjectService = subjectService;
        this.userService = userService;
    }
}
