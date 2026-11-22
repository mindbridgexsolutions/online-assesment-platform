package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.domain.AttemptStatus;
import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.entity.*;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.grading.AnswerGrader;
import com.mindbridgex.assessment.grading.GradeResult;
import com.mindbridgex.assessment.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GradingService {
    private final AttemptRepository attempts;
    private final AttemptQuestionRepository attemptQuestions;
    private final AttemptAnswerRepository answers;
    private final GradeRepository grades;
    private final ExamRepository exams;
    private final QuestionRepository questions;
    private final AnswerGrader grader;
    private final AuditService audit;

    /**
     * Grades without ever failing the caller's transaction: a grading error (for example a malformed snapshot) is logged and
     * the attempt stays SUBMITTED without a grade, to be retried by {@code AttemptService.regradeUngraded()}. The
     * method calls {@link #gradeAttempt} directly (no proxy), so its own failure does not mark the outer transaction
     * rollback-only and the submission itself is kept.
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public boolean tryGrade(Long attemptId) {
        try {
            gradeAttempt(attemptId);
            return true;
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(GradingService.class).error("Grading of attempt {} failed; it will be retried", attemptId, e);
            return false;
        }
    }

    /**
     * (Re)grades a submitted attempt. Answers that a teacher already graded by hand are kept.
     * Status becomes GRADED when nothing is waiting for manual grading, otherwise stays SUBMITTED.
     */
    @Transactional
    public Grade gradeAttempt(Long attemptId) {
        Attempt attempt = attempts.findById(attemptId).orElseThrow(() -> AppException.notFound("Attempt"));
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw AppException.conflict("The attempt is still in progress.");
        }
        Exam exam = exams.findById(attempt.getExamId()).orElseThrow(() -> AppException.notFound("Exam"));
        List<AttemptQuestion> qs = attemptQuestions.findByAttemptIdOrderByPositionAsc(attemptId);
        Map<Long, AttemptAnswer> byQ = answers.findByAttemptId(attemptId).stream().collect(Collectors.toMap(AttemptAnswer::getAttemptQuestionId, Function.identity()));
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        int pending = 0;
        for (AttemptQuestion q : qs) {
            AttemptAnswer a = byQ.get(q.getId());
            if (a == null) {
                a = new AttemptAnswer();
                a.setAttemptId(attemptId);
                a.setAttemptQuestionId(q.getId());
                byQ.put(q.getId(), a);
            }
            if ("DROPPED".equals(q.getVoidMode())) {
                // Withdrawn by a Super Administrator: neither scored nor counted in the maximum.
                a.setAwardedMarks(BigDecimal.ZERO.setScale(2));
                a.setCorrect(null);
                a.setNeedsManual(false);
                answers.save(a);
                continue;
            }
            max = max.add(q.getMarks());
            boolean manuallyGraded = a.getGradedBy() != null && q.getType() == QuestionType.ESSAY;
            if ("FREE_MARKS".equals(q.getVoidMode())) {
                a.setAwardedMarks(q.getMarks().setScale(2, RoundingMode.HALF_UP));
                a.setCorrect(true);
                a.setNeedsManual(false);
            } else if (!manuallyGraded) {
                GradeResult r = grader.grade(q, a.getAnswer(), a.isAnswered());
                a.setAwardedMarks(r.awarded());
                a.setCorrect(r.correct());
                a.setNeedsManual(r.needsManual());
            }
            if (a.isNeedsManual()) {
                pending++;
            }
            total = total.add(a.getAwardedMarks() == null ? BigDecimal.ZERO : a.getAwardedMarks());
            answers.save(a);
        }
        return finish(attempt, exam, total, max, pending);
    }

    private Grade finish(Attempt attempt, Exam exam, BigDecimal total, BigDecimal max, int pending) {
        BigDecimal score = total.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        Grade g = grades.findByAttemptId(attempt.getId()).orElseGet(Grade::new);
        g.setAttemptId(attempt.getId());
        g.setTotalScore(score);
        g.setMaxScore(max.setScale(2, RoundingMode.HALF_UP));
        g.setPercentage(max.signum() == 0 ? BigDecimal.ZERO : score.multiply(BigDecimal.valueOf(100)).divide(max, 2, RoundingMode.HALF_UP));
        g.setPassed(score.compareTo(exam.getPassingMarks()) >= 0);
        g.setPendingManual(pending);
        g.setGradedAt(Instant.now());
        attempt.setStatus(pending == 0 ? AttemptStatus.GRADED : AttemptStatus.SUBMITTED);
        attempts.save(attempt);
        return grades.save(g);
    }

    /**
     * Teacher awards marks to essay answers of an attempt. {@code marks} maps attempt-question id -> marks.
     */
    @Transactional
    public void gradeManually(Long attemptId, Map<Long, BigDecimal> marks, Map<Long, String> feedback, Long teacherId) {
        Attempt attempt = attempts.findById(attemptId).orElseThrow(() -> AppException.notFound("Attempt"));
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw AppException.conflict("The attempt is still in progress.");
        }
        Map<Long, AttemptQuestion> qs = attemptQuestions.findByAttemptIdOrderByPositionAsc(attemptId).stream().collect(Collectors.toMap(AttemptQuestion::getId, Function.identity()));
        for (Map.Entry<Long, BigDecimal> e : marks.entrySet()) {
            AttemptQuestion q = qs.get(e.getKey());
            if (q == null || q.getType() != QuestionType.ESSAY) {
                continue;
            }
            BigDecimal m = e.getValue();
            if (m == null) {
                continue;
            }
            if (m.signum() < 0 || m.compareTo(q.getMarks()) > 0) {
                throw AppException.bad("Marks must be between 0 and " + q.getMarks().stripTrailingZeros().toPlainString() + ".");
            }
            AttemptAnswer a = answers.findByAttemptIdAndAttemptQuestionId(attemptId, q.getId()).orElseThrow(() -> AppException.notFound("Answer"));
            a.setAwardedMarks(m.setScale(2, RoundingMode.HALF_UP));
            a.setCorrect(m.compareTo(q.getMarks()) >= 0);
            a.setNeedsManual(false);
            String fb = feedback == null ? null : feedback.get(q.getId());
            a.setFeedback(fb == null || fb.isBlank() ? null : (fb.length() > 2000 ? fb.substring(0, 2000) : fb));
            a.setGradedBy(teacherId);
            a.setGradedAt(Instant.now());
            answers.save(a);
        }
        gradeAttempt(attemptId);
        audit.log("ATTEMPT_GRADE_MANUAL", "Attempt", attemptId, null);
    }

    /**
     * After the teacher corrected a question in the bank, copies the new answer key into the snapshots of existing
     * attempts of this exam and grades them again. Manual essay grades are preserved.
     */
    @Transactional
    public int regradeQuestion(Long examId, Long questionId) {
        return regradeQuestion(examId, questionId, false);
    }

    /** @param syncContent also copy the question text, picture, explanation and option texts/pictures into running and finished attempts */
    @Transactional
    public int regradeQuestion(Long examId, Long questionId, boolean syncContent) {
        Question src = questions.findById(questionId).orElseThrow(() -> AppException.notFound("Question"));
        List<AttemptQuestion> aqs = attemptQuestions.findForExamQuestion(examId, questionId);
        Set<Long> attemptIds = new LinkedHashSet<>();
        for (AttemptQuestion aq : aqs) {
            syncKey(aq, src);
            if (syncContent) {
                aq.setText(src.getText());
                aq.setImagePath(src.getImagePath());
                aq.setExplanation(src.getExplanation());
            }
            attemptIds.add(aq.getAttemptId());
        }
        int n = 0;
        for (Long id : attemptIds) {
            Attempt a = attempts.findById(id).orElse(null);
            if (a != null && a.getStatus() != AttemptStatus.IN_PROGRESS) {
                gradeAttempt(id);
                n++;
            }
        }
        audit.log("EXAM_REGRADE", "Exam", examId, "question " + questionId + ", " + n + " attempt(s)");
        return n;
    }

    static void syncKey(AttemptQuestion aq, Question src) {
        aq.setNumericAnswer(src.getNumericAnswer());
        aq.setNumericTolerance(src.getNumericTolerance());
        aq.setCaseSensitive(src.isCaseSensitive());
        aq.setPartialMarking(src.isPartialMarking());
        if (aq.getType().isTextKey()) {
            List<SnapshotOption> opts = new ArrayList<>();
            for (QuestionOption o : src.getOptions()) {
                opts.add(new SnapshotOption(String.valueOf(o.getId()), o.getText(), null, true));
            }
            aq.setOptions(opts);
        } else {
            Map<String, QuestionOption> byKey = src.getOptions().stream().collect(Collectors.toMap(o -> String.valueOf(o.getId()), Function.identity()));
            List<SnapshotOption> opts = new ArrayList<>();
            for (SnapshotOption s : aq.getOptions()) {
                QuestionOption bank = byKey.get(s.getKey());
                if (bank == null) {
                    opts.add(s);
                } else {
                    SnapshotOption so = new SnapshotOption(s.getKey(), bank.getText(), bank.getMatchText(), bank.isCorrect());
                    so.setImagePath(bank.getImagePath());
                    opts.add(so);
                }
            }
            aq.setOptions(opts);
        }
    }

    public GradingService(final AttemptRepository attempts, final AttemptQuestionRepository attemptQuestions, final AttemptAnswerRepository answers, final GradeRepository grades, final ExamRepository exams, final QuestionRepository questions, final AnswerGrader grader, final AuditService audit) {
        this.attempts = attempts;
        this.attemptQuestions = attemptQuestions;
        this.answers = answers;
        this.grades = grades;
        this.exams = exams;
        this.questions = questions;
        this.grader = grader;
        this.audit = audit;
    }
}
