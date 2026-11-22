package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.CoPoService;
import com.mindbridgex.assessment.service.DocxQuestionParser;
import com.mindbridgex.assessment.service.ExamService;
import com.mindbridgex.assessment.service.ExamUploadService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/** "Upload Questions (.docx)" on the exam: upload, review, edit, confirm. */
@Controller
@RequestMapping("/teacher")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherExamUploadController {
    private final ExamUploadService service;
    private final DocxQuestionParser parser;
    private final ExamService exams;
    private final CoPoService copo;
    private final com.mindbridgex.assessment.repository.BatchRepository batches;
    private final com.mindbridgex.assessment.config.AppProperties props;
    private final com.mindbridgex.assessment.repository.UserRepository users;
    private final com.mindbridgex.assessment.service.QuestionService questionService;

    public TeacherExamUploadController(ExamUploadService service, DocxQuestionParser parser, ExamService exams, CoPoService copo,
                                       com.mindbridgex.assessment.repository.BatchRepository batches, com.mindbridgex.assessment.config.AppProperties props,
                                       com.mindbridgex.assessment.repository.UserRepository users, com.mindbridgex.assessment.service.QuestionService questionService) {
        this.users = users;
        this.questionService = questionService;
        this.batches = batches;
        this.props = props;
        this.service = service;
        this.parser = parser;
        this.exams = exams;
        this.copo = copo;
    }

    @GetMapping("/exams/upload-template.docx")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("question-paper-template.docx").build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).body(parser.template());
    }

    @GetMapping("/exams/{id}/upload")
    public String form(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, Model model) {
        model.addAttribute("exam", exams.get(user, id));
        return "teacher/exam-upload";
    }

    @PostMapping("/exams/{id}/upload")
    public String upload(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestParam("file") MultipartFile file, Model model) {
        try {
            Long sid = service.upload(user, id, file);
            return "redirect:/teacher/uploads/" + sid;
        } catch (AppException e) {
            model.addAttribute("exam", exams.get(user, id));
            model.addAttribute("fileError", e.getMessage());
            return "teacher/exam-upload";
        }
    }

    @GetMapping("/uploads/{sid}")
    public String preview(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, Model model) {
        service.recheck(user, sid);
        ExamUploadService.Session s = service.view(user, sid);
        model.addAttribute("s", s);
        model.addAttribute("letters", DocxQuestionParser.OPTION_LETTERS);
        if (s.examId() != null) {
            model.addAttribute("exam", exams.get(user, s.examId()));
        }
        return "teacher/exam-upload-preview";
    }

    @GetMapping("/uploads/{sid}/items/{iid}/edit")
    public String editForm(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, @PathVariable Long iid, Model model) {
        ExamUploadService.Session s = service.view(user, sid);
        ExamUploadService.Row row = s.rows().stream().filter(r -> r.id().equals(iid)).findFirst().orElseThrow(() -> AppException.notFound("Row"));
        model.addAttribute("s", s);
        model.addAttribute("row", row);
        if (s.examId() != null) {
            model.addAttribute("exam", exams.get(user, s.examId()));
        }
        model.addAttribute("cos", copo.cosForSubject(user, s.subjectId()));
        model.addAttribute("letters", DocxQuestionParser.OPTION_LETTERS);
        model.addAttribute("blooms", com.mindbridgex.assessment.domain.Bloom.values());
        return "teacher/exam-upload-edit";
    }

    @PostMapping("/uploads/{sid}/items/{iid}/edit")
    public String edit(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, @PathVariable Long iid, @RequestParam(required = false) String text,
                       @RequestParam(required = false) List<String> optionText, @RequestParam(required = false) List<String> correct, @RequestParam(required = false) String marks,
                       @RequestParam(required = false) String co, @RequestParam(required = false) String difficulty, @RequestParam(required = false) String bloom, @RequestParam(required = false) String unit, @RequestParam(required = false) MultipartFile questionImage,
                       @RequestParam(required = false) List<MultipartFile> optionImage, @RequestParam(required = false) boolean removeQuestionImage,
                       @RequestParam(required = false) List<Integer> removeOptionImage, HttpServletResponse res) {
        try {
            service.edit(user, sid, iid, new ExamUploadService.Edit(text, optionText, correct == null ? "" : String.join(",", correct), marks, co, difficulty, bloom, unit, questionImage, optionImage,
                    removeQuestionImage, removeOptionImage));
            Flash.success(res, "Row updated and checked again.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/teacher/uploads/" + sid;
    }

    @PostMapping("/uploads/{sid}/items/{iid}/delete")
    public String delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, @PathVariable Long iid, HttpServletResponse res) {
        service.deleteItem(user, sid, iid);
        Flash.success(res, "Row removed from this upload.");
        return "redirect:/teacher/uploads/" + sid;
    }

    @GetMapping("/uploads/{sid}/add-questions")
    public String addQuestionsForm(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, @RequestParam(required = false) String q, Model model) {
        ExamUploadService.Session s = service.view(user, sid);
        model.addAttribute("s", s);
        model.addAttribute("fQ", q);
        model.addAttribute("found", questionService.search(user, s.subjectId(), null, null, null, null, q, 0, 100).getContent());
        return "teacher/paper-add-questions";
    }

    @PostMapping("/uploads/{sid}/add-questions")
    public String addQuestions(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, @RequestParam(required = false) List<Long> ids, HttpServletResponse res) {
        try {
            int n = service.addFromBank(user, sid, ids);
            Flash.success(res, n + " question(s) added to this paper.");
            return "redirect:/teacher/uploads/" + sid;
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
            return "redirect:/teacher/uploads/" + sid + "/add-questions";
        }
    }

    @GetMapping("/uploads/{sid}/publish")
    public String publishForm(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, Model model) {
        service.recheck(user, sid);
        ExamUploadService.Session s = service.view(user, sid);
        model.addAttribute("s", s);
        model.addAttribute("batchPicks", service.batchPicks());
        model.addAttribute("students", users.findActiveByRole(com.mindbridgex.assessment.domain.RoleType.STUDENT));
        java.time.LocalDateTime now = java.time.LocalDateTime.now(props.zone()).withSecond(0).withNano(0);
        model.addAttribute("defaultStart", now.plusMinutes(10).toString().substring(0, 16));
        model.addAttribute("defaultEnd", now.plusDays(1).toString().substring(0, 16));
        model.addAttribute("defaultTitle", s.fileName().replaceFirst("(?i)\\.(docx|xlsx|csv)$", ""));
        return "teacher/paper-publish";
    }

    @PostMapping("/uploads/{sid}/publish")
    public String publish(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, @RequestParam String title, @RequestParam(required = false) String instructions,
                          @RequestParam(defaultValue = "60") int durationMinutes,
                          @RequestParam @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") java.time.LocalDateTime startAt,
                          @RequestParam @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") java.time.LocalDateTime endAt,
                          @RequestParam(required = false) java.math.BigDecimal passingMarks, @RequestParam(required = false) List<Long> studentIds, HttpServletResponse res) {
        try {
            String usernames = studentIds == null ? "" : users.findByIdIn(studentIds).stream().filter(u -> u.getRoles().contains(com.mindbridgex.assessment.domain.RoleType.STUDENT))
                    .map(com.mindbridgex.assessment.entity.User::getUsername).collect(java.util.stream.Collectors.joining(","));
            ExamUploadService.PublishResult r = service.publishPaper(user, sid,
                    new ExamUploadService.PublishForm(title, instructions, durationMinutes, startAt, endAt, passingMarks, null, usernames));
            if (r.published()) {
                Flash.success(res, "Sent to " + (studentIds == null ? 0 : studentIds.size()) + " student(s) with " + r.questions() + " question(s). " + r.message().replace("Published to students.", "").trim());
            } else {
                Flash.error(res, r.message());
            }
            return "redirect:/teacher/exams/" + r.examId();
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
            return "redirect:/teacher/uploads/" + sid + "/publish";
        }
    }

    /** Bulk include / exclude from the validation dashboard. */
    @PostMapping("/uploads/{sid}/exclude")
    public String exclude(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, @RequestParam(required = false) List<Long> ids,
                          @RequestParam boolean excluded, HttpServletResponse res) {
        service.setExcluded(user, sid, ids, excluded);
        Flash.success(res, excluded ? "Selected questions will be left out of the merge." : "Selected questions are included again.");
        return "redirect:/teacher/uploads/" + sid;
    }

    @PostMapping("/uploads/{sid}/items/{iid}/move-image")
    public String moveImage(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, @PathVariable Long iid,
                            @RequestParam String from, @RequestParam String to, HttpServletResponse res) {
        service.moveImage(user, sid, iid, from, to);
        Flash.success(res, "Picture moved.");
        return "redirect:/teacher/uploads/" + sid + "/items/" + iid + "/edit";
    }

    @PostMapping("/uploads/{sid}/items/{iid}/keep")
    public String keep(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, @PathVariable Long iid, @RequestParam boolean keep) {
        service.setKeep(user, sid, iid, keep);
        return "redirect:/teacher/uploads/" + sid;
    }

    @PostMapping("/uploads/{sid}/confirm")
    public String confirm(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, HttpServletResponse res) {
        ExamUploadService.Session s = service.view(user, sid);
        try {
            ExamUploadService.ConfirmResult r = service.confirm(user, sid);
            Flash.success(res, r.created() + " question(s) saved to the Question Bank and added to the exam" + (r.skipped() > 0 ? ", " + r.skipped() + " skipped" : "") + (r.failed() > 0 ? ", " + r.failed() + " failed" : "") + ".");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
            return "redirect:/teacher/uploads/" + sid;
        }
        return "redirect:/teacher/exams/" + s.examId();
    }

    @PostMapping("/uploads/{sid}/discard")
    public String discard(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, HttpServletResponse res) {
        ExamUploadService.Session s = service.view(user, sid);
        service.discard(user, sid);
        Flash.info(res, "Paper deleted. Nothing was saved to the Question Bank.");
        return s.examId() == null ? "redirect:/teacher/questions" : "redirect:/teacher/exams/" + s.examId();
    }

    /** Explicit merge of the paper into the Question Bank (never automatic). */
    @PostMapping("/uploads/{sid}/merge-to-bank")
    public String mergeToBank(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, HttpServletResponse res) {
        try {
            ExamUploadService.ConfirmResult r = service.mergeToBank(user, sid);
            Flash.success(res, r.created() + " question(s) merged into the Question Bank" + (r.skipped() > 0 ? ", " + r.skipped() + " skipped" : "") + (r.failed() > 0 ? ", " + r.failed() + " failed" : "") + ".");
            return "redirect:/teacher/questions";
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
            return "redirect:/teacher/uploads/" + sid;
        }
    }

    /** The paper as a Word file in the upload template, to edit in Word and upload back. */
    @GetMapping("/uploads/{sid}/download.docx")
    public ResponseEntity<byte[]> downloadDocx(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid) {
        ExamUploadService.Session s = service.view(user, sid);
        String base = s.fileName().replaceAll("(?i)\\.(docx|xlsx|csv)$", "").replaceAll("[^A-Za-z0-9._ -]", "_");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename((base.isBlank() ? "paper" : base) + "-edit.docx").build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).body(service.exportDocx(user, sid));
    }

    @PostMapping("/uploads/{sid}/replace")
    public String replace(@AuthenticationPrincipal AuthUser user, @PathVariable Long sid, @RequestParam("file") MultipartFile file, HttpServletResponse res) {
        try {
            service.replaceFromDocx(user, sid, file);
            Flash.success(res, "Paper replaced with the rows of your Word file and checked again.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/teacher/uploads/" + sid;
    }
}
