package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.entity.AuditLog;
import com.mindbridgex.assessment.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.Instant;
import java.time.LocalDate;

@Controller
@PreAuthorize("hasRole(\'ADMIN\')")
public class AdminAuditController {
    private static final int PAGE_SIZE = 50;
    private final AuditLogRepository auditLogs;
    private final AppProperties props;

    @GetMapping("/admin/audit")
    @Transactional(readOnly = true)
    public String audit(@RequestParam(required = false) String action, @RequestParam(required = false) String actor, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "0") int page, Model model) {
        Instant fromInstant = from == null ? null : from.atStartOfDay(props.zone()).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(props.zone()).toInstant();
        Page<AuditLog> result = auditLogs.findAll(AuditLogRepository.filter(action, actor, fromInstant, toInstant), PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        model.addAttribute("page", result);
        model.addAttribute("actions", auditLogs.distinctActions());
        model.addAttribute("action", action);
        model.addAttribute("actor", actor);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "admin/audit";
    }

    public AdminAuditController(final AuditLogRepository auditLogs, final AppProperties props) {
        this.auditLogs = auditLogs;
        this.props = props;
    }
}
