package com.mindbridgex.assessment.grading;

import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.entity.AttemptQuestion;
import com.mindbridgex.assessment.entity.SnapshotOption;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnswerGraderTest {

    private final AnswerGrader grader = new AnswerGrader();

    private static AttemptQuestion q(QuestionType type, String marks, String negative, SnapshotOption... options) {
        AttemptQuestion q = new AttemptQuestion();
        q.setType(type);
        q.setMarks(new BigDecimal(marks));
        q.setNegativeMarks(new BigDecimal(negative));
        q.setOptions(new ArrayList<>(List.of(options)));
        return q;
    }

    private static SnapshotOption o(String key, String text, boolean correct) {
        return new SnapshotOption(key, text, null, correct);
    }

    private static SnapshotOption pair(String key, String left, String right) {
        return new SnapshotOption(key, left, right, true);
    }

    private static void assertMarks(String expected, GradeResult r) {
        assertEquals(0, new BigDecimal(expected).compareTo(r.awarded()), "awarded " + r.awarded());
    }

    @Test
    void singleChoiceCorrectAndWrong() {
        AttemptQuestion q = q(QuestionType.MCQ_SINGLE, "2", "0", o("1", "A", false), o("2", "B", true));
        assertMarks("2", grader.grade(q, "2", true));
        assertTrue(grader.grade(q, "2", true).correct());
        assertMarks("0", grader.grade(q, "1", true));
        assertFalse(grader.grade(q, "1", true).correct());
    }

    @Test
    void trueFalse() {
        AttemptQuestion q = q(QuestionType.TRUE_FALSE, "1", "0", o("10", "True", true), o("11", "False", false));
        assertMarks("1", grader.grade(q, "10", true));
        assertMarks("0", grader.grade(q, "11", true));
    }

    @Test
    void unansweredScoresZeroAndNeverGetsNegativeMarks() {
        AttemptQuestion q = q(QuestionType.MCQ_SINGLE, "4", "1", o("1", "A", true), o("2", "B", false));
        GradeResult r = grader.grade(q, null, false);
        assertMarks("0", r);
        assertFalse(r.needsManual());
    }

    @Test
    void wrongAnswerLosesNegativeMarks() {
        AttemptQuestion q = q(QuestionType.MCQ_SINGLE, "4", "1", o("1", "A", true), o("2", "B", false));
        assertMarks("-1", grader.grade(q, "2", true));
        assertMarks("4", grader.grade(q, "1", true));
    }

    @Test
    void multiChoiceAllOrNothingWithoutPartialMarking() {
        AttemptQuestion q = q(QuestionType.MCQ_MULTI, "4", "1", o("1", "A", true), o("2", "B", true), o("3", "C", false));
        assertMarks("4", grader.grade(q, "[\"1\",\"2\"]", true));
        assertMarks("-1", grader.grade(q, "[\"1\"]", true));
        assertMarks("-1", grader.grade(q, "[\"1\",\"2\",\"3\"]", true));
    }

    @Test
    void multiChoicePartialMarking() {
        AttemptQuestion q = q(QuestionType.MCQ_MULTI, "4", "0", o("1", "A", true), o("2", "B", true), o("3", "C", false), o("4", "D", false));
        q.setPartialMarking(true);
        assertMarks("2", grader.grade(q, "[\"1\"]", true));            // 1 of 2 right
        assertMarks("2", grader.grade(q, "[\"1\",\"2\",\"3\"]", true)); // 2 right, 1 wrong -> (2-1)/2 of 4
        assertMarks("0", grader.grade(q, "[\"3\",\"4\"]", true));       // never below zero
        assertMarks("4", grader.grade(q, "[\"2\",\"1\"]", true));
    }

    @Test
    void multiChoiceIgnoresUnknownKeysAndGarbage() {
        AttemptQuestion q = q(QuestionType.MCQ_MULTI, "2", "0", o("1", "A", true), o("2", "B", false));
        assertMarks("2", grader.grade(q, "[\"1\",\"999\"]", true));
        assertMarks("0", grader.grade(q, "not json", true));
    }

    @Test
    void fillBlankIgnoresCaseAndSpacingByDefault() {
        AttemptQuestion q = q(QuestionType.FILL_BLANK, "1", "0", o("1", "New Delhi", true), o("2", "Delhi", true));
        assertMarks("1", grader.grade(q, "  new   delhi ", true));
        assertMarks("1", grader.grade(q, "DELHI", true));
        assertMarks("0", grader.grade(q, "Mumbai", true));
    }

    @Test
    void fillBlankCaseSensitive() {
        AttemptQuestion q = q(QuestionType.FILL_BLANK, "1", "0", o("1", "NaCl", true));
        q.setCaseSensitive(true);
        assertMarks("1", grader.grade(q, "NaCl", true));
        assertMarks("0", grader.grade(q, "nacl", true));
    }

    @Test
    void shortAnswerIsProportionalToKeywordsFound() {
        AttemptQuestion q = q(QuestionType.SHORT_ANSWER, "3", "0", o("1", "red", true), o("2", "blue", true), o("3", "yellow", true));
        assertMarks("3", grader.grade(q, "Red, Blue and Yellow", true));
        assertMarks("2", grader.grade(q, "red and blue", true));
        assertMarks("1", grader.grade(q, "only yellow", true));
        assertMarks("0", grader.grade(q, "green", true));
    }

    @Test
    void matchingIsProportionalToCorrectPairs() {
        AttemptQuestion q = q(QuestionType.MATCHING, "3", "0", pair("1", "India", "Delhi"), pair("2", "Japan", "Tokyo"), pair("3", "Italy", "Rome"));
        assertMarks("3", grader.grade(q, "{\"1\":\"1\",\"2\":\"2\",\"3\":\"3\"}", true));
        assertMarks("1", grader.grade(q, "{\"1\":\"1\",\"2\":\"3\",\"3\":\"2\"}", true));
        assertMarks("0", grader.grade(q, "{\"1\":\"2\"}", true));
    }

    @Test
    void numericWithinTolerance() {
        AttemptQuestion q = q(QuestionType.NUMERIC, "2", "0");
        q.setNumericAnswer(new BigDecimal("3.14"));
        q.setNumericTolerance(new BigDecimal("0.01"));
        assertMarks("2", grader.grade(q, "3.14", true));
        assertMarks("2", grader.grade(q, "3.15", true));
        assertMarks("2", grader.grade(q, " 3.13 ", true));
        assertMarks("0", grader.grade(q, "3.2", true));
        assertMarks("0", grader.grade(q, "abc", true));
    }

    @Test
    void numericWithoutToleranceMustMatchExactly() {
        AttemptQuestion q = q(QuestionType.NUMERIC, "1", "0");
        q.setNumericAnswer(new BigDecimal("144"));
        q.setNumericTolerance(BigDecimal.ZERO);
        assertMarks("1", grader.grade(q, "144", true));
        assertMarks("1", grader.grade(q, "144.0", true));
        assertMarks("0", grader.grade(q, "145", true));
    }

    @Test
    void essayWaitsForTeacherAndIsNeverPenalised() {
        AttemptQuestion q = q(QuestionType.ESSAY, "10", "2");
        GradeResult r = grader.grade(q, "my essay", true);
        assertTrue(r.needsManual());
        assertNull(r.correct());
        assertMarks("0", r);
    }

    @Test
    void emptyEssayIsNotSentForManualGrading() {
        AttemptQuestion q = q(QuestionType.ESSAY, "10", "0");
        GradeResult r = grader.grade(q, null, false);
        assertFalse(r.needsManual());
        assertMarks("0", r);
    }
}
