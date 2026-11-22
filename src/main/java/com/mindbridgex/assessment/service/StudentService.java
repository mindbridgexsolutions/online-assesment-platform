package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.domain.AttemptStatus;
import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.dto.StudentAttemptRow;
import com.mindbridgex.assessment.entity.*;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.grading.Strategies;
import com.mindbridgex.assessment.repository.*;
import com.mindbridgex.assessment.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read side of the student area: exam list, results and answer review (with result-policy checks).
 */
@Service
public class StudentService {

    public enum State {
        UPCOMING, OPEN, IN_PROGRESS, CLOSED;
    }


    public record ExamCard(Exam exam, State state, int attemptsUsed, Long inProgressAttemptId, boolean canStart, StudentAttemptRow lastResult) {
    }


    public record ReviewOption(String text, boolean correct, boolean chosen, String imagePath) {
    }


    public record ReviewPair(String left, String chosenRight, String correctRight, boolean ok) {
    }


    public record ReviewQuestion(int number, QuestionType type, String text, String imagePath, BigDecimal marks, BigDecimal awarded, Boolean correct, boolean needsManual, boolean answered, String explanation, String feedback, List<ReviewOption> options, List<ReviewPair> pairs, String givenText, String expectedText) {
    }


    public record Summary(int total, int attempted, int notAttempted, int correct, int partly, int incorrect, int awaiting) {
    }

    public record Review(Attempt attempt, Exam exam, Grade grade, StudentAttemptRow row, boolean detailed, List<ReviewQuestion> questions, Summary summary) {
    }

    private final ExamRepository exams;
    private final AttemptRepository attempts;
    private final AttemptQuestionRepository attemptQuestions;
    private final AttemptAnswerRepository answers;
    private final GradeRepository grades;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ExamCard> dashboard(AuthUser student) {
        Instant now = clock.instant();
        List<Exam> visible = exams.findVisibleToStudent(student.getId());
        List<StudentAttemptRow> rows = attempts.findStudentRows(student.getId());
        Map<Long, List<StudentAttemptRow>> byExam = rows.stream().collect(Collectors.groupingBy(StudentAttemptRow::examId));
        List<ExamCard> cards = new ArrayList<>();
        for (Exam e : visible) {
            List<StudentAttemptRow> mine = byExam.getOrDefault(e.getId(), List.of());
            Optional<StudentAttemptRow> running = mine.stream().filter(r -> r.status() == AttemptStatus.IN_PROGRESS).findFirst();
            State state;
            if (running.isPresent()) {
                state = State.IN_PROGRESS;
            } else if (now.isBefore(e.getStartAt())) {
                state = State.UPCOMING;
            } else if (now.isBefore(e.getEndAt())) {
                state = State.OPEN;
            } else {
                state = State.CLOSED;
            }
            boolean canStart = state == State.OPEN && mine.size() < e.getMaxAttempts();
            StudentAttemptRow last = mine.stream().filter(r -> r.status() != AttemptStatus.IN_PROGRESS).findFirst().orElse(null);
            cards.add(new ExamCard(e, state, mine.size(), running.map(StudentAttemptRow::attemptId).orElse(null), canStart || state == State.IN_PROGRESS, last));
        }
        return cards;
    }

    @Transactional(readOnly = true)
    public ExamCard card(AuthUser student, Long examId) {
        if (!exams.isAssignedToStudent(examId, student.getId())) {
            throw AppException.notFound("Exam");
        }
        return dashboard(student).stream().filter(c -> c.exam().getId().equals(examId)).findFirst().orElseThrow(() -> AppException.notFound("Exam"));
    }

    @Transactional(readOnly = true)
    public List<StudentAttemptRow> results(AuthUser student) {
        return attempts.findStudentRows(student.getId()).stream().filter(r -> r.status() != AttemptStatus.IN_PROGRESS).toList();
    }

    @Transactional(readOnly = true)
    public Review review(AuthUser student, Long attemptId) {
        Attempt a = attempts.findByIdAndStudentId(attemptId, student.getId()).orElseThrow(() -> AppException.notFound("Attempt"));
        StudentAttemptRow row = attempts.findStudentRows(student.getId()).stream().filter(r -> r.attemptId().equals(attemptId)).findFirst().orElseThrow(() -> AppException.notFound("Attempt"));
        if (a.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw AppException.conflict("This attempt is still in progress.");
        }
        if (!row.isResultVisible(clock.instant())) {
            throw AppException.forbidden("Your result is not available yet.");
        }
        Exam exam = exams.findById(a.getExamId()).orElseThrow();
        Grade g = grades.findByAttemptId(attemptId).orElseThrow(() -> AppException.notFound("Result"));
        List<ReviewQuestion> qs = buildReview(attemptId);
        int attempted = 0, correct = 0, partly = 0, incorrect = 0, awaiting = 0;
        for (ReviewQuestion q : qs) {
            if (q.answered()) attempted++;
            if (q.needsManual()) awaiting++;
            else if (!q.answered()) continue;
            else if (Boolean.TRUE.equals(q.correct())) correct++;
            else if (q.awarded() != null && q.awarded().signum() > 0) partly++;
            else incorrect++;
        }
        Summary summary = new Summary(qs.size(), attempted, qs.size() - attempted, correct, partly, incorrect, awaiting);
        // Students only get the summary; the question-by-question review is not shown.
        return new Review(a, exam, g, row, false, List.of(), summary);
    }

    private List<ReviewQuestion> buildReview(Long attemptId) {
        Map<Long, AttemptAnswer> byQ = answers.findByAttemptId(attemptId).stream().collect(Collectors.toMap(AttemptAnswer::getAttemptQuestionId, Function.identity()));
        List<ReviewQuestion> out = new ArrayList<>();
        int n = 1;
        for (AttemptQuestion q : attemptQuestions.findByAttemptIdOrderByPositionAsc(attemptId)) {
            AttemptAnswer a = byQ.get(q.getId());
            String given = a == null ? null : a.getAnswer();
            boolean answered = a != null && a.isAnswered();
            List<ReviewOption> opts = new ArrayList<>();
            List<ReviewPair> pairs = new ArrayList<>();
            String givenText = null;
            String expectedText = null;
            switch (q.getType()) {
                case MCQ_SINGLE, TRUE_FALSE -> {
                    for (SnapshotOption o : q.getOptions()) {
                        opts.add(new ReviewOption(o.getText(), o.isCorrect(), answered && o.getKey().equals(given.trim()), o.getImagePath()));
                    }
                }
                case MCQ_MULTI -> {
                    Set<String> chosen = answered ? Strategies.parseSet(given) : Set.of();
                    for (SnapshotOption o : q.getOptions()) {
                        opts.add(new ReviewOption(o.getText(), o.isCorrect(), chosen.contains(o.getKey()), o.getImagePath()));
                    }
                }
                case MATCHING -> {
                    Map<String, String> chosen = answered ? Strategies.parseMap(given) : Map.of();
                    Map<String, String> rightText = q.getOptions().stream().collect(Collectors.toMap(SnapshotOption::getKey, SnapshotOption::getMatchText));
                    for (SnapshotOption o : q.getOptions()) {
                        String pick = chosen.get(o.getKey());
                        pairs.add(new ReviewPair(o.getText(), pick == null ? null : rightText.get(pick), o.getMatchText(), o.getKey().equals(pick)));
                    }
                }
                case FILL_BLANK, SHORT_ANSWER -> {
                    givenText = given;
                    expectedText = q.getOptions().stream().map(SnapshotOption::getText).collect(Collectors.joining(", "));
                }
                case NUMERIC -> {
                    givenText = given;
                    expectedText = q.getNumericAnswer() == null ? "" : q.getNumericAnswer().stripTrailingZeros().toPlainString() + (q.getNumericTolerance() != null && q.getNumericTolerance().signum() > 0 ? " (±" + q.getNumericTolerance().stripTrailingZeros().toPlainString() + ")" : "");
                }
                case ESSAY -> givenText = given;
            }
            out.add(new ReviewQuestion(n++, q.getType(), q.getText(), q.getImagePath(), q.getMarks(), a == null ? null : a.getAwardedMarks(), a == null ? null : a.getCorrect(), a != null && a.isNeedsManual(), answered, q.getExplanation(), a == null ? null : a.getFeedback(), opts, pairs, givenText, expectedText));
        }
        return out;
    }

    public StudentService(final ExamRepository exams, final AttemptRepository attempts, final AttemptQuestionRepository attemptQuestions, final AttemptAnswerRepository answers, final GradeRepository grades, final Clock clock) {
        this.exams = exams;
        this.attempts = attempts;
        this.attemptQuestions = attemptQuestions;
        this.answers = answers;
        this.grades = grades;
        this.clock = clock;
    }
}
