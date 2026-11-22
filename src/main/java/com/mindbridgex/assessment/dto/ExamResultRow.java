package com.mindbridgex.assessment.dto;

import com.mindbridgex.assessment.domain.AttemptStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** One attempt in an exam's result table (teacher / admin view). */
public record ExamResultRow(
        Long attemptId,
        Long studentId,
        String studentName,
        String rollNo,
        int attemptNo,
        AttemptStatus status,
        Instant submittedAt,
        BigDecimal score,
        BigDecimal maxScore,
        BigDecimal percentage,
        Boolean passed,
        Integer pendingManual,
        int focusLosses) {

    public boolean isGraded() {
        return score != null;
    }

    public boolean isFinal() {
        return status == AttemptStatus.GRADED;
    }
}
