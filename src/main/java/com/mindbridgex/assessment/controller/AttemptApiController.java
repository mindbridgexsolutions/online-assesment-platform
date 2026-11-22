package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AttemptService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * JSON endpoints used by the exam player (autosave, focus events, submit).
 */
@RestController
@RequestMapping(value = "/api/attempts", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole(\'STUDENT\')")
public class AttemptApiController {
    public static final String SESSION_HEADER = "X-Player-Session";


    public record AnswersRequest(List<AttemptService.AnswerInput> answers) {
    }

    private final AttemptService attemptService;

    @PostMapping(value = "/{id}/answers", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AttemptService.SaveResult save(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestHeader(SESSION_HEADER) String session, @RequestBody AnswersRequest body) {
        return attemptService.saveAnswers(user, id, session, body.answers());
    }

    @PostMapping("/{id}/focus-lost")
    public AttemptService.EventResult focusLost(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestHeader(SESSION_HEADER) String session) {
        return attemptService.focusLost(user, id, session);
    }

    @PostMapping(value = "/{id}/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> submit(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestHeader(value = SESSION_HEADER, required = false) String session, @RequestBody(required = false) AnswersRequest body) {
        var a = attemptService.submit(user, id, session, body == null ? null : body.answers());
        return Map.of("status", a.getStatus().name(), "redirect", "/student/results");
    }

    public AttemptApiController(final AttemptService attemptService) {
        this.attemptService = attemptService;
    }
}
