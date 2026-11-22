package com.mindbridgex.assessment.grading;

import java.math.BigDecimal;

/** Outcome of grading a single answer. {@code correct} is null while a manual grade is pending. */
public record GradeResult(BigDecimal awarded, Boolean correct, boolean needsManual) {

    public static GradeResult manualPending() {
        return new GradeResult(BigDecimal.ZERO, null, true);
    }
}
