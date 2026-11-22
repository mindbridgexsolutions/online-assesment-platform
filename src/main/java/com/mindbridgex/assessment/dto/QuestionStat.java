package com.mindbridgex.assessment.dto;

import com.mindbridgex.assessment.domain.QuestionType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Per-question aggregate across all graded attempts of an exam. */
public record QuestionStat(
        Long sourceQuestionId,
        QuestionType type,
        String text,
        Long attempts,
        Long answered,
        Long correct,
        Double avgMarks,
        BigDecimal maxMarks) {

    /** Difficulty index: share of attempts that answered this question correctly (0-100). */
    public BigDecimal percentCorrect() {
        if (attempts == null || attempts == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(correct * 100.0 / attempts).setScale(1, RoundingMode.HALF_UP);
    }

    public BigDecimal percentAnswered() {
        if (attempts == null || attempts == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(answered * 100.0 / attempts).setScale(1, RoundingMode.HALF_UP);
    }

    public BigDecimal avgMarksRounded() {
        return avgMarks == null ? BigDecimal.ZERO : BigDecimal.valueOf(avgMarks).setScale(2, RoundingMode.HALF_UP);
    }

    /** Human readable difficulty bucket derived from the difficulty index. */
    public String band() {
        double p = percentCorrect().doubleValue();
        if (p >= 80) {
            return "Easy";
        }
        if (p >= 40) {
            return "Moderate";
        }
        return "Hard";
    }
}
