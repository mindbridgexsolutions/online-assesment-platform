package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.FileStorageService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.time.Duration;

/**
 * Serves question attachments (images inline; PDF and Word files as downloads). Names are unguessable UUIDs and strictly validated.
 * Staff (teacher, admin, super admin, HoD, IQAC) may open any attachment; a student may open only pictures that are part of one of their own attempts.
 */
@Controller
public class FileController {
    private final FileStorageService storage;
    private final JdbcClient jdbc;

    @GetMapping("/files/q/{name:[a-f0-9-]{36}\\.(?:png|jpg|gif|webp|pdf|docx)}")
    public ResponseEntity<Resource> image(@AuthenticationPrincipal AuthUser user, @PathVariable String name) {
        if (user != null && !mayOpen(user, name)) {
            throw AppException.notFound("Image");
        }
        Resource resource = storage.load(name);
        boolean document = name.endsWith(".pdf") || name.endsWith(".docx");
        return ResponseEntity.ok().contentType(storage.mediaType(name)).header("Content-Disposition", document ? "attachment; filename=\"question-attachment" + name.substring(name.lastIndexOf('.')) + "\"" : "inline").cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate()).header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "default-src \'none\'; sandbox").body(resource);
    }

    /** Staff always; a student only when the file is a picture of a question or option in one of that student's attempts. */
    private boolean mayOpen(AuthUser user, String name) {
        if (user.isSuperAdmin() || user.isAdmin() || user.isTeacher() || user.isHod() || user.isIqac() || !user.isStudent()) {
            return true;
        }
        return jdbc.sql("select exists (select 1 from attempt_questions aq join attempts a on a.id = aq.attempt_id "
                + "where a.student_id = :u and (aq.image_path = :n or aq.options::text like :like))")
                .param("u", user.getId()).param("n", name).param("like", "%" + name + "%").query(Boolean.class).single();
    }

    public FileController(final FileStorageService storage, final JdbcClient jdbc) {
        this.storage = storage;
        this.jdbc = jdbc;
    }
}
