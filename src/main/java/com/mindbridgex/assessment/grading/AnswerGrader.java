package com.mindbridgex.assessment.grading;

import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.entity.AttemptQuestion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/**
 * Applies the right {@link GradingStrategy} and the negative-marking rule:
 * unanswered questions score 0; an answered auto-graded question that earns nothing loses its negative marks.
 */
@Component
public class AnswerGrader {

    private final Map<QuestionType, GradingStrategy> strategies = new EnumMap<>(QuestionType.class);

    public AnswerGrader() {
        Strategies.all().forEach(s -> strategies.put(s.type(), s));
    }

    public GradeResult grade(AttemptQuestion q, String answer, boolean answered) {
        if (!answered) {
            return new GradeResult(BigDecimal.ZERO.setScale(2), false, false);
        }
        GradeResult r = strategies.get(q.getType()).grade(q, answer);
        if (r.needsManual()) {
            return r;
        }
        if (r.awarded().signum() <= 0 && q.getNegativeMarks() != null && q.getNegativeMarks().signum() > 0) {
            return new GradeResult(q.getNegativeMarks().negate().setScale(2, RoundingMode.HALF_UP), false, false);
        }
        return r;
    }
}
