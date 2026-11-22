package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.dto.BatchForm;
import com.mindbridgex.assessment.entity.Batch;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.service.BatchService;
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
@RequestMapping("/admin/batches")
@PreAuthorize("hasRole(\'ADMIN\')")
public class AdminBatchController {
    private final BatchService batchService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("batches", batchService.list());
        return "admin/batches";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new BatchForm());
        return "admin/batch-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", batchService.toForm(id));
        return "admin/batch-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") BatchForm form, BindingResult br, Model model, HttpServletResponse response) {
        return save(null, form, br, model, response);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") BatchForm form, BindingResult br, Model model, HttpServletResponse response) {
        return save(id, form, br, model, response);
    }

    private String save(Long id, BatchForm form, BindingResult br, Model model, HttpServletResponse response) {
        form.setId(id);
        if (br.hasErrors()) {
            model.addAttribute("errors", FormErrors.of(br));
            return "admin/batch-form";
        }
        try {
            Batch b = batchService.save(form);
            Flash.success(response, "Batch \'" + b.getName() + "\' saved.");
            return "redirect:/admin/batches/" + b.getId();
        } catch (AppException e) {
            model.addAttribute("errors", List.of(e.getMessage()));
            return "admin/batch-form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("batch", batchService.get(id));
        model.addAttribute("members", batchService.members(id));
        return "admin/batch-detail";
    }

    @PostMapping("/{id}/students")
    public String addStudents(@PathVariable Long id, @RequestParam String identifiers, HttpServletResponse response) {
        try {
            BatchService.AddResult r = batchService.addStudents(id, identifiers);
            StringBuilder msg = new StringBuilder().append(r.added()).append(" student(s) added");
            if (r.alreadyMember() > 0) {
                msg.append(", ").append(r.alreadyMember()).append(" already in the batch");
            }
            if (!r.notFound().isEmpty()) {
                msg.append(". Not found: ").append(String.join(", ", r.notFound()));
                Flash.error(response, msg.toString());
            } else {
                Flash.success(response, msg.toString());
            }
        } catch (AppException e) {
            Flash.error(response, e.getMessage());
        }
        return "redirect:/admin/batches/" + id;
    }

    @PostMapping("/{id}/students/{studentId}/remove")
    public String removeStudent(@PathVariable Long id, @PathVariable Long studentId, HttpServletResponse response) {
        batchService.removeStudent(id, studentId);
        Flash.success(response, "Student removed from the batch.");
        return "redirect:/admin/batches/" + id;
    }

    @PostMapping("/{id}/toggle-active")
    public String toggle(@PathVariable Long id, @RequestParam boolean active, HttpServletResponse response) {
        batchService.setActive(id, active);
        Flash.success(response, active ? "Batch activated." : "Batch deactivated.");
        return "redirect:/admin/batches";
    }

    public AdminBatchController(final BatchService batchService) {
        this.batchService = batchService;
    }
}
