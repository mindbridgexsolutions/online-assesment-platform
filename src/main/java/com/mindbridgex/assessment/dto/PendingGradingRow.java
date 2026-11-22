package com.mindbridgex.assessment.dto;

import java.time.Instant;

public record PendingGradingRow(
        Long attemptId,
        Long examId,
        String examTitle,
        String studentName,
        String rollNo,
        Instant submittedAt,
        Integer pendingManual) {
}
