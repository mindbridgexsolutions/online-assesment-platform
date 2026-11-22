package com.mindbridgex.assessment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net: submits attempts whose time ran out even if the student closed the browser.
 */
@Component
public class AttemptAutoSubmitJob {
    private static final Logger log = LoggerFactory.getLogger(AttemptAutoSubmitJob.class);
    private final AttemptService attemptService;

    @Scheduled(fixedDelayString = "${app.attempts.auto-submit-interval-ms:15000}", initialDelay = 15000)
    public void run() {
        try {
            int n = attemptService.closeExpired();
            if (n > 0) {
                log.info("Auto-submitted {} expired attempt(s)", n);
            }
            int regraded = attemptService.regradeUngraded();
            if (regraded > 0) {
                log.warn("Re-graded {} attempt(s) whose first grading failed", regraded);
            }
        } catch (RuntimeException e) {
            log.error("Auto-submit job failed", e);
        }
    }

    public AttemptAutoSubmitJob(final AttemptService attemptService) {
        this.attemptService = attemptService;
    }
}
