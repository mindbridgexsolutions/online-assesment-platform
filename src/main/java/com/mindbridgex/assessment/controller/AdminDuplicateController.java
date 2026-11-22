package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.service.DuplicateService;
import com.mindbridgex.assessment.util.Flash;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@Controller
@RequestMapping("/admin/duplicates")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDuplicateController {
    private final DuplicateService service;

    public AdminDuplicateController(DuplicateService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("groups", service.groups());
        return "admin/duplicates";
    }

    @PostMapping("/merge")
    public String merge(@RequestParam Long keep, @RequestParam(required = false) List<Long> remove, HttpServletResponse res) {
        try {
            int n = service.merge(keep, remove);
            Flash.success(res, n + " duplicate(s) merged into the question you kept. Exams and past results are unchanged.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/admin/duplicates";
    }
}
