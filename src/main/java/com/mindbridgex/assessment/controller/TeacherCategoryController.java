package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AccessService;
import com.mindbridgex.assessment.service.CategoryService;
import com.mindbridgex.assessment.util.Flash;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/teacher/categories")
@PreAuthorize("hasRole(\'TEACHER\')")
public class TeacherCategoryController {
    private final CategoryService categories;
    private final AccessService access;

    @GetMapping
    public String list(@AuthenticationPrincipal AuthUser user, Model model) {
        model.addAttribute("categories", categories.list(user));
        model.addAttribute("subjects", access.teacherSubjects(user));
        return "teacher/categories";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AuthUser user, @RequestParam Long subjectId, @RequestParam String name, HttpServletResponse res) {
        run(res, "Category added.", () -> categories.create(user, subjectId, name));
        return "redirect:/teacher/categories";
    }

    @PostMapping("/{id}/rename")
    public String rename(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestParam String name, HttpServletResponse res) {
        run(res, "Category renamed.", () -> categories.rename(user, id, name));
        return "redirect:/teacher/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, HttpServletResponse res) {
        run(res, "Category deleted.", () -> categories.delete(user, id));
        return "redirect:/teacher/categories";
    }

    private void run(HttpServletResponse res, String ok, Runnable r) {
        try {
            r.run();
            Flash.success(res, ok);
        } catch (AppException e) {
            if (e.getStatus().value() == 403) {
                throw e;
            }
            Flash.error(res, e.getMessage());
        }
    }

    public TeacherCategoryController(final CategoryService categories, final AccessService access) {
        this.categories = categories;
        this.access = access;
    }
}
