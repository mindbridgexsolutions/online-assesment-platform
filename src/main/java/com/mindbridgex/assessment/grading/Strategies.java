package com.mindbridgex.assessment.grading;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.entity.AttemptQuestion;
import com.mindbridgex.assessment.entity.SnapshotOption;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** All grading strategies, kept together because each is only a few lines. */
public final class Strategies {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Strategies() {
    }

    public static List<GradingStrategy> all() {
        return List.of(new Single(QuestionType.MCQ_SINGLE), new Single(QuestionType.TRUE_FALSE), new Multi(),
                new FillBlank(), new ShortAnswer(), new Matching(), new Numeric(), new Essay());
    }

    static BigDecimal fraction(AttemptQuestion q, double ratio) {
        double r = Math.max(0, Math.min(1, ratio));
        return q.getMarks().multiply(BigDecimal.valueOf(r)).setScale(2, RoundingMode.HALF_UP);
    }

    static GradeResult full(AttemptQuestion q) {
        return new GradeResult(q.getMarks().setScale(2, RoundingMode.HALF_UP), true, false);
    }

    static GradeResult none() {
        return new GradeResult(BigDecimal.ZERO.setScale(2), false, false);
    }

    static String norm(String s, boolean caseSensitive) {
        String t = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        return caseSensitive ? t : t.toLowerCase(Locale.ROOT);
    }

    public static Set<String> parseSet(String answer) {
        return parseKeySet(answer);
    }

    static Set<String> parseKeySet(String answer) {
        try {
            List<String> l = JSON.readValue(answer, new TypeReference<List<String>>() {
            });
            return new HashSet<>(l);
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    public static Map<String, String> parseMap(String answer) {
        try {
            return new HashMap<>(JSON.readValue(answer, new TypeReference<Map<String, String>>() {
            }));
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /** MCQ with one correct option, and True/False. */
    static final class Single implements GradingStrategy {
        private final QuestionType type;

        Single(QuestionType type) {
            this.type = type;
        }

        @Override
        public QuestionType type() {
            return type;
        }

        @Override
        public GradeResult grade(AttemptQuestion q, String answer) {
            String chosen = answer.trim();
            boolean ok = q.getOptions().stream().anyMatch(o -> o.isCorrect() && o.getKey().equals(chosen));
            return ok ? full(q) : none();
        }
    }

    /** Several correct options; all-or-nothing, or partial marking when enabled on the question. */
    static final class Multi implements GradingStrategy {
        @Override
        public QuestionType type() {
            return QuestionType.MCQ_MULTI;
        }

        @Override
        public GradeResult grade(AttemptQuestion q, String answer) {
            Set<String> chosen = parseKeySet(answer);
            Set<String> correct = new HashSet<>();
            Set<String> all = new HashSet<>();
            for (SnapshotOption o : q.getOptions()) {
                all.add(o.getKey());
                if (o.isCorrect()) {
                    correct.add(o.getKey());
                }
            }
            chosen.retainAll(all);
            if (correct.isEmpty()) {
                return none();
            }
            if (chosen.equals(correct)) {
                return full(q);
            }
            if (!q.isPartialMarking()) {
                return none();
            }
            long right = chosen.stream().filter(correct::contains).count();
            long wrong = chosen.size() - right;
            double ratio = (double) (right - wrong) / correct.size();
            BigDecimal awarded = fraction(q, ratio);
            return new GradeResult(awarded, false, false);
        }
    }

    /** Any one accepted answer, exact after trimming / collapsing whitespace. */
    static final class FillBlank implements GradingStrategy {
        @Override
        public QuestionType type() {
            return QuestionType.FILL_BLANK;
        }

        @Override
        public GradeResult grade(AttemptQuestion q, String answer) {
            String given = norm(answer, q.isCaseSensitive());
            boolean ok = q.getOptions().stream().anyMatch(o -> norm(o.getText(), q.isCaseSensitive()).equals(given));
            return ok ? full(q) : none();
        }
    }

    /** Marks proportional to the share of expected keywords present in the answer. */
    static final class ShortAnswer implements GradingStrategy {
        @Override
        public QuestionType type() {
            return QuestionType.SHORT_ANSWER;
        }

        @Override
        public GradeResult grade(AttemptQuestion q, String answer) {
            String given = norm(answer, q.isCaseSensitive());
            List<SnapshotOption> keywords = q.getOptions();
            if (keywords.isEmpty()) {
                return none();
            }
            long hit = keywords.stream().filter(k -> given.contains(norm(k.getText(), q.isCaseSensitive()))).count();
            if (hit == keywords.size()) {
                return full(q);
            }
            return new GradeResult(fraction(q, (double) hit / keywords.size()), false, false);
        }
    }

    /** Answer is a JSON object left-key -> right-key; marks are proportional to correctly matched pairs. */
    static final class Matching implements GradingStrategy {
        @Override
        public QuestionType type() {
            return QuestionType.MATCHING;
        }

        @Override
        public GradeResult grade(AttemptQuestion q, String answer) {
            Map<String, String> given = parseMap(answer);
            int total = q.getOptions().size();
            if (total == 0) {
                return none();
            }
            long right = q.getOptions().stream().filter(o -> o.getKey().equals(given.get(o.getKey()))).count();
            if (right == total) {
                return full(q);
            }
            return new GradeResult(fraction(q, (double) right / total), false, false);
        }
    }

    /** |given - expected| <= tolerance. */
    static final class Numeric implements GradingStrategy {
        @Override
        public QuestionType type() {
            return QuestionType.NUMERIC;
        }

        @Override
        public GradeResult grade(AttemptQuestion q, String answer) {
            if (q.getNumericAnswer() == null) {
                return none();
            }
            BigDecimal given;
            try {
                given = new BigDecimal(answer.trim().replace(",", "").replace(" ", ""));
            } catch (NumberFormatException e) {
                return none();
            }
            BigDecimal tol = q.getNumericTolerance() == null ? BigDecimal.ZERO : q.getNumericTolerance().abs();
            return given.subtract(q.getNumericAnswer()).abs().compareTo(tol) <= 0 ? full(q) : none();
        }
    }

    /** Always waits for a teacher. */
    static final class Essay implements GradingStrategy {
        @Override
        public QuestionType type() {
            return QuestionType.ESSAY;
        }

        @Override
        public GradeResult grade(AttemptQuestion q, String answer) {
            return GradeResult.manualPending();
        }
    }
}
