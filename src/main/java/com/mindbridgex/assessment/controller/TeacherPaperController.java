package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AccessService;
import com.mindbridgex.assessment.service.ExamUploadService;
import com.mindbridgex.assessment.util.Flash;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * "Question papers": every uploaded file is kept as one named paper (a list of MCQs) that the teacher can review, edit (in the browser or in Word),
 * merge with another paper, and finally merge into the Question Bank. Nothing is merged automatically.
 */
@Controller
@RequestMapping("/teacher/papers")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherPaperController {
    private final ExamUploadService service;
    private final AccessService access;

    public TeacherPaperController(ExamUploadService service, AccessService access) {
        this.service = service;
        this.access = access;
    }

    /** Papers no longer have a page of their own: uploads are listed on the Question bank page. */
    @GetMapping
    public String list() {
        return "redirect:/teacher/questions";
    }

    @PostMapping("/upload")
    public String upload(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) Long subjectId, @RequestParam("file") MultipartFile file, HttpServletResponse res) {
        try {
            if (subjectId == null) {
                throw AppException.bad("Choose the subject of this paper.");
            }
            Long sid = service.uploadPaper(user, subjectId, file);
            ExamUploadService.SamePaper same = service.sameAsPaper(user, sid);
            if (same != null) {
                service.discard(user, sid);
                Flash.error(res, "This file has exactly the same questions as your paper \"" + same.name() + "\", so it was not added again. That paper is highlighted in the list below.");
                return "redirect:/teacher/questions?highlightPaper=" + same.id() + "#paper-" + same.id();
            }
            return "redirect:/teacher/uploads/" + sid;
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
            return "redirect:/teacher/questions";
        }
    }

    @PostMapping("/merge")
    public String merge(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) List<Long> ids, @RequestParam(required = false) String title, HttpServletResponse res) {
        try {
            Long sid = service.mergePapers(user, ids, title);
            Flash.success(res, "Papers combined into one new paper. Review it, edit anything you like, then merge it into the Question Bank.");
            return "redirect:/teacher/uploads/" + sid;
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
            return "redirect:/teacher/questions";
        }
    }
}
