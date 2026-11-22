package com.mindbridgex.assessment.dto;

import com.mindbridgex.assessment.domain.AttemptStatus;
import com.mindbridgex.assessment.domain.ResultPolicy;

import java.math.BigDecimal;
import java.time.Instant;

/** One of a student's own attempts, with the information needed to decide result visibility. */
public record StudentAttemptRow(
        Long attemptId,
        Long examId,
        String examTitle,
        int attemptNo,
        AttemptStatus status,
        Instant startedAt,
        Instant submittedAt,
        BigDecimal score,
        BigDecimal maxScore,
        BigDecimal percentage,
        Boolean passed,
        Integer pendingManual,
        ResultPolicy resultPolicy,
        Instant examEndAt,
        boolean resultsReleased,
        boolean reviewAllowed,
        Instant resultsPublishAt) {

    public boolean isResultVisible() {
        return isResultVisible(Instant.now());
    }

    public boolean isResultVisible(Instant now) {
        if (status == AttemptStatus.IN_PROGRESS || score == null) {
            return false;
        }
        return switch (resultPolicy) {
            case IMMEDIATE -> true;
            case AFTER_WINDOW -> !now.isBefore(examEndAt);
            case MANUAL -> resultsReleased;
            case SCHEDULED -> resultsPublishAt != null && !now.isBefore(resultsPublishAt);
        };
    }
}
