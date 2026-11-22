package com.mindbridgex.assessment.grading;

import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.entity.AttemptQuestion;

/** One implementation per question type. Implementations are pure: no I/O, no clock. */
public interface GradingStrategy {

    QuestionType type();

    /**
     * Grades a non-blank answer. Marks are between 0 and {@code q.getMarks()}; negative marking is applied by the caller
     * only when the result is a completely wrong answer.
     */
    GradeResult grade(AttemptQuestion q, String answer);
}
