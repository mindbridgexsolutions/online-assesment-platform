package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.dto.UserForm;
import com.mindbridgex.assessment.entity.User;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.BatchService;
import com.mindbridgex.assessment.service.UserImportService;
import com.mindbridgex.assessment.service.UserService;
import com.mindbridgex.assessment.util.Flash;
import com.mindbridgex.assessment.util.FormErrors;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole(\'ADMIN\')")
public class AdminUserController {
    private static final int PAGE_SIZE = 20;
    private final UserService userService;
    private final UserImportService importService;
    private final BatchService batchService;

    @GetMapping
    public String list(@RequestParam(required = false) String q, @RequestParam(required = false) RoleType role, @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page, Model model) {
        Page<User> result = userService.search(role, q, active, PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("fullName").ascending()));
        model.addAttribute("page", result);
        model.addAttribute("q", q);
        model.addAttribute("role", role);
        model.addAttribute("active", active);
        model.addAttribute("roleTypes", RoleType.values());
        return "admin/users";
    }

    /** Roles the signed-in person may hand out: only a Super Admin can create Admin or Super Admin accounts. */
    private static RoleType[] grantable(AuthUser me) {
        return me.isSuperAdmin() ? RoleType.values() : new RoleType[] {RoleType.TEACHER, RoleType.HOD, RoleType.IQAC, RoleType.STUDENT};
    }

    private static boolean privileged(java.util.Collection<RoleType> roles) {
        return roles != null && (roles.contains(RoleType.ADMIN) || roles.contains(RoleType.SUPER_ADMIN));
    }

    private void guardTarget(AuthUser me, Long id) {
        if (!me.isSuperAdmin() && privileged(userService.get(id).getRoles())) {
            throw AppException.forbidden("Only a Super Administrator can change administrator accounts.");
        }
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal AuthUser me, Model model) {
        model.addAttribute("form", new UserForm());
        model.addAttribute("roleTypes", grantable(me));
        model.addAttribute("editing", false);
        return "admin/user-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") UserForm form, BindingResult br, @AuthenticationPrincipal AuthUser me, Model model) {
        model.addAttribute("roleTypes", grantable(me));
        if (!me.isSuperAdmin() && privileged(form.getRoles())) {
            throw AppException.forbidden("Only a Super Administrator can create administrator accounts.");
        }
        model.addAttribute("editing", false);
        if (br.hasErrors()) {
            model.addAttribute("errors", FormErrors.of(br));
            return "admin/user-form";
        }
        try {
            UserService.Created created = userService.create(form);
            model.addAttribute("account", created.user());
            model.addAttribute("password", created.initialPassword());
            model.addAttribute("headline", "User created");
            return "admin/user-credentials";
        } catch (AppException e) {
            model.addAttribute("errors", java.util.List.of(e.getMessage()));
            return "admin/user-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal AuthUser me, Model model) {
        guardTarget(me, id);
        model.addAttribute("form", userService.toForm(id));
        model.addAttribute("roleTypes", grantable(me));
        model.addAttribute("editing", true);
        return "admin/user-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") UserForm form, BindingResult br, @AuthenticationPrincipal AuthUser me, Model model, HttpServletResponse response) {
        guardTarget(me, id);
        if (!me.isSuperAdmin() && privileged(form.getRoles())) {
            throw AppException.forbidden("Only a Super Administrator can grant administrator roles.");
        }
        model.addAttribute("roleTypes", grantable(me));
        model.addAttribute("editing", true);
        if (br.hasErrors()) {
            model.addAttribute("errors", FormErrors.of(br));
            return "admin/user-form";
        }
        try {
            User u = userService.update(id, form, me.getId());
            Flash.success(response, "User \'" + u.getUsername() + "\' updated.");
            return "redirect:/admin/users";
        } catch (AppException e) {
            model.addAttribute("errors", java.util.List.of(e.getMessage()));
            return "admin/user-form";
        }
    }

    @PostMapping("/{id}/toggle-active")
    public String toggleActive(@PathVariable Long id, @RequestParam boolean active, @AuthenticationPrincipal AuthUser me, HttpServletResponse response) {
        guardTarget(me, id);
        try {
            userService.setActive(id, active, me.getId());
            Flash.success(response, active ? "User activated." : "User deactivated and signed out everywhere.");
        } catch (AppException e) {
            Flash.error(response, e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, @AuthenticationPrincipal AuthUser me, Model model) {
        guardTarget(me, id);
        User u = userService.get(id);
        String temp = userService.resetPassword(id);
        model.addAttribute("account", u);
        model.addAttribute("password", temp);
        model.addAttribute("headline", "Password reset");
        return "admin/user-credentials";
    }

    @PostMapping("/{id}/unlock")
    public String unlock(@PathVariable Long id, @AuthenticationPrincipal AuthUser me, HttpServletResponse response) {
        guardTarget(me, id);
        userService.unlock(id);
        Flash.success(response, "Account unlocked.");
        return "redirect:/admin/users";
    }

    // ---------- bulk upload ----------
    @GetMapping("/import")
    public String importForm(Model model) {
        model.addAttribute("batches", batchService.activeBatches());
        model.addAttribute("maxRows", UserImportService.MAX_ROWS);
        return "admin/user-import";
    }

    @PostMapping("/import")
    public String importUsers(@RequestParam("file") MultipartFile file, @RequestParam(required = false) Long batchId, Model model) {
        try {
            model.addAttribute("result", importService.importStudents(file, batchId));
            return "admin/user-import-result";
        } catch (AppException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("batches", batchService.activeBatches());
            model.addAttribute("maxRows", UserImportService.MAX_ROWS);
            return "admin/user-import";
        }
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() {
        String csv = "username,full_name,email,roll_no,phone,password,batch_code\r\n" + "asha.patil,Asha Patil,asha.patil@example.com,2024-001,9876543210,,\r\n" + "ravi.kumar,Ravi Kumar,ravi.kumar@example.com,2024-002,,,\r\n";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("students-template.csv").build().toString()).contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(csv.getBytes(StandardCharsets.UTF_8));
    }

    public AdminUserController(final UserService userService, final UserImportService importService, final BatchService batchService) {
        this.userService = userService;
        this.importService = importService;
        this.batchService = batchService;
    }
}
