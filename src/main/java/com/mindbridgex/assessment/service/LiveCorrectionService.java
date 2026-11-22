package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.domain.AttemptStatus;
import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.entity.Attempt;
import com.mindbridgex.assessment.entity.Exam;
import com.mindbridgex.assessment.entity.Question;
import com.mindbridgex.assessment.entity.QuestionOption;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.AttemptRepository;
import com.mindbridgex.assessment.repository.ExamRepository;
import com.mindbridgex.assessment.repository.QuestionRepository;
import com.mindbridgex.assessment.security.AuthUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Lets a Super Administrator fix a wrong question while an exam is running, without stopping it:
 * correct the key, accept more than one option, withdraw the question or give everybody the marks,
 * and recalculate the scores of everyone who already submitted.
 */
@Service
public class LiveCorrectionService {
    private final JdbcClient jdbc;
    private final ExamRepository exams;
    private final QuestionRepository questions;
    private final AttemptRepository attempts;
    private final GradingService grading;
    private final AuditService audit;

    public record Opt(Long id, String text, boolean correct, String imagePath) {
    }

    public record LiveQuestion(Long questionId, String text, QuestionType type, BigDecimal marks, String voidMode, long attemptCount, List<Opt> options) {
    }

    public record LiveExam(Exam exam, long inProgress, long finished, List<LiveQuestion> questions) {
    }

    public LiveCorrectionService(JdbcClient jdbc, ExamRepository exams, QuestionRepository questions, AttemptRepository attempts, GradingService grading, AuditService audit) {
        this.jdbc = jdbc;
        this.exams = exams;
        this.questions = questions;
        this.attempts = attempts;
        this.grading = grading;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Exam> examsToCorrect() {
        return exams.findAll().stream().filter(e -> e.getStatus() != com.mindbridgex.assessment.domain.ExamStatus.DRAFT || attempts.countByExamId(e.getId()) > 0)
                .sorted((a, b) -> b.getStartAt().compareTo(a.getStartAt())).toList();
    }

    @Transactional(readOnly = true)
    public LiveExam view(AuthUser user, Long examId) {
        OverrideService.requireSuper(user);
        Exam e = exams.findById(examId).orElseThrow(() -> AppException.notFound("Exam"));
        long running = attempts.findByExamId(examId).stream().filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS).count();
        long finished = attempts.findByExamId(examId).size() - running;
        List<Long> ids = jdbc.sql("""
                select question_id from exam_questions where exam_id = :e
                union
                select aq.source_question_id from attempt_questions aq join attempts a on a.id = aq.attempt_id
                 where a.exam_id = :e and aq.source_question_id is not null
                """).param("e", examId).query(Long.class).list();
        List<LiveQuestion> out = new ArrayList<>();
        for (Question q : questions.findByIdIn(ids)) {
            String mode = jdbc.sql("select coalesce((select void_mode from exam_questions where exam_id = :e and question_id = :q), "
                    + "(select max(aq.void_mode) from attempt_questions aq join attempts a on a.id = aq.attempt_id where a.exam_id = :e and aq.source_question_id = :q), 'NONE')")
                    .param("e", examId).param("q", q.getId()).query(String.class).single();
            BigDecimal marks = jdbc.sql("select coalesce((select marks from exam_questions where exam_id = :e and question_id = :q), "
                    + "(select max(aq.marks) from attempt_questions aq join attempts a on a.id = aq.attempt_id where a.exam_id = :e and aq.source_question_id = :q), 1)")
                    .param("e", examId).param("q", q.getId()).query(BigDecimal.class).single();
            long n = jdbc.sql("select count(*) from attempt_questions aq join attempts a on a.id = aq.attempt_id where a.exam_id = :e and aq.source_question_id = :q")
                    .param("e", examId).param("q", q.getId()).query(Long.class).single();
            List<Opt> opts = new ArrayList<>();
            for (QuestionOption o : q.getOptions()) {
                opts.add(new Opt(o.getId(), o.getText(), o.isCorrect(), o.getImagePath()));
            }
            out.add(new LiveQuestion(q.getId(), q.getText(), q.getType(), marks, mode == null ? "NONE" : mode, n, opts));
        }
        out.sort((a, b) -> Long.compare(a.questionId(), b.questionId()));
        return new LiveExam(e, running, finished, out);
    }

    /** Several options of a single-answer question are all accepted from now on (running attempts included). */
    @Transactional
    public int acceptOptions(AuthUser user, Long examId, Long questionId, Collection<Long> optionIds, String reason) {
        OverrideService.requireSuper(user);
        String why = OverrideService.reason(reason);
        Question q = questions.findById(questionId).orElseThrow(() -> AppException.notFound("Question"));
        if (q.getType() != QuestionType.MCQ_SINGLE && q.getType() != QuestionType.TRUE_FALSE) {
            throw AppException.bad("Accepting extra options works for single-answer questions. For multiple-answer questions, edit the answer key instead.");
        }
        if (optionIds == null || optionIds.isEmpty()) {
            throw AppException.bad("Tick the option(s) that should also be accepted as correct.");
        }
        Set<Long> valid = new java.util.HashSet<>();
        for (QuestionOption o : q.getOptions()) {
            valid.add(o.getId());
        }
        int added = 0;
        for (QuestionOption o : q.getOptions()) {
            if (optionIds.contains(o.getId()) && !o.isCorrect()) {
                o.setCorrect(true);
                added++;
            }
        }
        for (Long id : optionIds) {
            if (!valid.contains(id)) {
                throw AppException.bad("That option does not belong to the question.");
            }
        }
        questions.save(q);
        int n = grading.regradeQuestion(examId, questionId, false);
        audit.log("OVERRIDE_ACCEPT_OPTIONS", "Exam", examId, "question " + questionId + ": +" + added + " accepted option(s), " + n + " submitted attempt(s) re-scored - " + why);
        return n;
    }

    /** After the question was edited in the bank: copy the corrected text and key into every attempt and re-score. */
    @Transactional
    public int pushEdit(AuthUser user, Long examId, Long questionId, String reason) {
        OverrideService.requireSuper(user);
        String why = OverrideService.reason(reason);
        int n = grading.regradeQuestion(examId, questionId, true);
        audit.log("OVERRIDE_PUSH_EDIT", "Exam", examId, "question " + questionId + " copied to all attempts, " + n + " submitted attempt(s) re-scored - " + why);
        return n;
    }

    /** mode: NONE (restore), DROPPED (remove the question from scoring) or FREE_MARKS (everyone gets the marks). */
    @Transactional
    public int setVoid(AuthUser user, Long examId, Long questionId, String mode, String reason) {
        OverrideService.requireSuper(user);
        String why = OverrideService.reason(reason);
        if (!List.of("NONE", "DROPPED", "FREE_MARKS").contains(mode)) {
            throw AppException.bad("Unknown action.");
        }
        jdbc.sql("update exam_questions set void_mode = :m where exam_id = :e and question_id = :q").param("m", mode).param("e", examId).param("q", questionId).update();
        jdbc.sql("update attempt_questions set void_mode = :m where source_question_id = :q and attempt_id in (select id from attempts where exam_id = :e)")
                .param("m", mode).param("q", questionId).param("e", examId).update();
        int n = rescoreFinished(examId);
        audit.log("OVERRIDE_QUESTION_" + mode, "Exam", examId, "question " + questionId + ", " + n + " submitted attempt(s) re-scored - " + why);
        return n;
    }

    @Transactional
    public int recalculate(AuthUser user, Long examId, String reason) {
        OverrideService.requireSuper(user);
        String why = OverrideService.reason(reason);
        int n = rescoreFinished(examId);
        audit.log("OVERRIDE_RECALCULATE", "Exam", examId, n + " attempt(s) re-scored - " + why);
        return n;
    }

    private int rescoreFinished(Long examId) {
        int n = 0;
        for (Attempt a : attempts.findByExamId(examId)) {
            if (a.getStatus() != AttemptStatus.IN_PROGRESS) {
                grading.gradeAttempt(a.getId());
                n++;
            }
        }
        return n;
    }
}
