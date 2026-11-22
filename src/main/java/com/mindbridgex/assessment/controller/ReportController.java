package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AttemptService;
import com.mindbridgex.assessment.service.GradingService;
import com.mindbridgex.assessment.service.ReportService;
import com.mindbridgex.assessment.util.Flash;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/reports")
@PreAuthorize("hasAnyRole(\'TEACHER\',\'ADMIN\')")
public class ReportController {
    private final ReportService reports;
    private final AttemptService attemptService;
    private final GradingService gradingService;
    private final com.mindbridgex.assessment.service.MarksMatrixService matrix;

    @GetMapping
    public String index(@AuthenticationPrincipal AuthUser user, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", reports.examsFor(user, page, 20));
        return "reports/index";
    }

    @GetMapping("/exams/{id}")
    public String exam(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, Model model) {
        model.addAttribute("s", reports.summary(user, id));
        return "reports/exam";
    }

    @GetMapping("/exams/{id}/export.xlsx")
    public ResponseEntity<byte[]> excel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return file(reports.excel(user, id), "exam-" + id + "-results.xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/exams/{id}/export.pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return file(reports.pdf(user, id), "exam-" + id + "-results.pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/exams/{id}/marks-sheet.xlsx")
    public ResponseEntity<byte[]> marksSheetExcel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return file(matrix.excel(user, id), "exam-" + id + "-student-question-marks.xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/exams/{id}/marks-sheet.pdf")
    public ResponseEntity<byte[]> marksSheetPdf(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return file(matrix.pdf(user, id), "exam-" + id + "-student-question-marks.pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/attempts/{attemptId}/scorecard.pdf")
    public ResponseEntity<byte[]> scorecard(@AuthenticationPrincipal AuthUser user, @PathVariable Long attemptId) {
        reports.attemptFor(user, attemptId).orElseThrow(() -> AppException.notFound("Attempt"));
        return file(reports.scorecard(attemptId), "scorecard-" + attemptId + ".pdf", MediaType.APPLICATION_PDF);
    }

    @PostMapping("/exams/{id}/close-attempts")
    public String closeAttempts(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, HttpServletResponse res) {
        reports.examFor(user, id);
        int n = attemptService.closeAllForExam(id);
        Flash.success(res, n + " running attempt(s) closed and graded.");
        return "redirect:/reports/exams/" + id;
    }

    @PostMapping("/exams/{id}/regrade/{questionId}")
    public String regrade(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable Long questionId, HttpServletResponse res) {
        reports.examFor(user, id);
        int n = gradingService.regradeQuestion(id, questionId);
        Flash.success(res, "Answer key re-applied; " + n + " attempt(s) regraded.");
        return "redirect:/reports/exams/" + id;
    }

    private static ResponseEntity<byte[]> file(byte[] data, String name, MediaType type) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString()).contentType(type).body(data);
    }

    public ReportController(final ReportService reports, final AttemptService attemptService, final GradingService gradingService, final com.mindbridgex.assessment.service.MarksMatrixService matrix) {
        this.matrix = matrix;
        this.reports = reports;
        this.attemptService = attemptService;
        this.gradingService = gradingService;
    }
}
