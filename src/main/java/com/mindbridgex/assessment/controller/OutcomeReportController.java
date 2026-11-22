package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AccessService;
import com.mindbridgex.assessment.service.OutcomeReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** CO-PO attainment reports: per course, per class (batch) and per student; screen, Excel and PDF. */
@Controller
@RequestMapping("/reports/outcomes")
@PreAuthorize("hasAnyRole('TEACHER','IQAC')")
public class OutcomeReportController {
    private final OutcomeReportService service;
    private final AccessService access;

    public OutcomeReportController(OutcomeReportService service, AccessService access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) Long subjectId, @RequestParam(required = false) Long batchId,
                       @RequestParam(required = false) Long examId, Model model) {
        model.addAttribute("subjects", access.teacherSubjects(user));
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("batchId", batchId);
        model.addAttribute("examId", examId);
        if (subjectId != null) {
            model.addAttribute("exams", service.examsOf(user, subjectId));
            model.addAttribute("batches", service.batchesOf(user, subjectId));
            model.addAttribute("r", service.build(user, new OutcomeReportService.Scope(subjectId, batchId, examId)));
        }
        return "reports/outcomes";
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> excel(@AuthenticationPrincipal AuthUser user, @RequestParam Long subjectId, @RequestParam(required = false) Long batchId, @RequestParam(required = false) Long examId) {
        return file(service.excel(service.build(user, new OutcomeReportService.Scope(subjectId, batchId, examId))), "co-po-attainment.xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/export.pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AuthUser user, @RequestParam Long subjectId, @RequestParam(required = false) Long batchId, @RequestParam(required = false) Long examId) {
        return file(service.pdf(service.build(user, new OutcomeReportService.Scope(subjectId, batchId, examId)), null), "co-po-attainment.pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/student/{studentId}.pdf")
    public ResponseEntity<byte[]> student(@AuthenticationPrincipal AuthUser user, @PathVariable Long studentId, @RequestParam Long subjectId, @RequestParam(required = false) Long examId) {
        return file(service.pdf(service.build(user, new OutcomeReportService.Scope(subjectId, null, examId)), studentId), "co-attainment-student-" + studentId + ".pdf", MediaType.APPLICATION_PDF);
    }

    private static ResponseEntity<byte[]> file(byte[] data, String name, MediaType type) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString()).contentType(type).body(data);
    }
}
