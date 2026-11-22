package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AccessService;
import com.mindbridgex.assessment.service.QuestionImportExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/teacher/questions")
@PreAuthorize("hasRole(\'TEACHER\')")
public class TeacherQuestionImportController {
    private final QuestionImportExportService service;
    private final AccessService access;

    @GetMapping("/import")
    public String form(@AuthenticationPrincipal AuthUser user, Model model) {
        model.addAttribute("subjects", access.teacherSubjects(user));
        return "teacher/question-import";
    }

    @PostMapping("/import")
    public String upload(@AuthenticationPrincipal AuthUser user, @RequestParam("file") MultipartFile file, Model model) {
        model.addAttribute("subjects", access.teacherSubjects(user));
        if (file == null || file.isEmpty()) {
            model.addAttribute("fileError", "Choose a .xlsx or .csv file.");
            return "teacher/question-import";
        }
        try {
            model.addAttribute("result", service.importFile(user, file));
        } catch (AppException e) {
            model.addAttribute("fileError", e.getMessage());
        }
        return "teacher/question-import";
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template(@RequestParam(required = false) String subjectCode) {
        String code = subjectCode != null && subjectCode.matches("[A-Za-z0-9._-]{1,40}") ? subjectCode : null;
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("questions-template.csv").build().toString()).contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(("﻿" + service.templateCsv(code)).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/import/template.docx")
    public ResponseEntity<byte[]> templateDocx(@RequestParam(required = false) String subjectCode) {
        String code = subjectCode != null && subjectCode.matches("[A-Za-z0-9._-]{1,40}") ? subjectCode : null;
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("questions-template.docx").build().toString()).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).body(service.templateDocx(code));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) Long subjectId) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("questions.xlsx").build().toString()).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(service.export(user, subjectId));
    }

    public TeacherQuestionImportController(final QuestionImportExportService service, final AccessService access) {
        this.service = service;
        this.access = access;
    }
}
