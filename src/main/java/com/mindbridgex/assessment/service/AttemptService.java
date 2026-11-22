package com.mindbridgex.assessment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.domain.AttemptStatus;
import com.mindbridgex.assessment.domain.ExamStatus;
import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.domain.SubmitReason;
import com.mindbridgex.assessment.entity.*;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.grading.Strategies;
import com.mindbridgex.assessment.repository.*;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.util.IpMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns the life of an exam attempt: start (with a frozen snapshot of the paper), autosave, focus events,
 * submit and expiry. The server clock is the only authority on time.
 */
@Service
public class AttemptService {

    private static final Logger log = LoggerFactory.getLogger(AttemptService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_TEXT = 20_000;

    public record PlayerOption(String key, String text, String imagePath) {
    }

    public record PlayerQuestion(Long id, int number, QuestionType type, String text, String imagePath, BigDecimal marks,
                                 List<PlayerOption> options, List<PlayerOption> rightItems, String answer, boolean flagged,
                                 boolean answered, Set<String> selected, Map<String, String> pairs) {
    }

    public record PlayerView(Attempt attempt, Exam exam, List<PlayerQuestion> questions, String playerSession, long serverNow) {
    }

    public record AnswerInput(Long questionId, String answer, boolean flagged) {
    }

    private final AttemptRepository attempts;
    private final AttemptQuestionRepository attemptQuestions;
    private final AttemptAnswerRepository answers;
    private final ExamRepository exams;
    private final ExamQuestionRepository examQuestions;
    private final ExamQuestionRuleRepository rules;
    private final QuestionRepository questions;
    private final GradingService grading;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public AttemptService(AttemptRepository attempts, AttemptQuestionRepository attemptQuestions, AttemptAnswerRepository answers,
                          ExamRepository exams, ExamQuestionRepository examQuestions, ExamQuestionRuleRepository rules,
                          QuestionRepository questions, GradingService grading, AuditService audit, AppProperties props,
                          Clock clock, PlatformTransactionManager tm) {
        this.attempts = attempts;
        this.attemptQuestions = attemptQuestions;
        this.answers = answers;
        this.exams = exams;
        this.examQuestions = examQuestions;
        this.rules = rules;
        this.questions = questions;
        this.grading = grading;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(tm);
    }

    // ------------------------------------------------------------------ start / resume

    /** Starts a new attempt, or returns the one already running (page refresh, second device, crash recovery). */
    public Attempt startOrResume(AuthUser student, Long examId, String ip, String userAgent) {
        try {
            return tx.execute(s -> doStart(student, examId, ip, userAgent));
        } catch (DataIntegrityViolationException e) {
            // two requests raced: the partial unique index let only one through, so the other one resumes it
            return attempts.findFirstByExamIdAndStudentIdAndStatus(examId, student.getId(), AttemptStatus.IN_PROGRESS)
                    .orElseThrow(() -> AppException.conflict("Could not start the exam. Please try again."));
        }
    }

    private Attempt doStart(AuthUser student, Long examId, String ip, String userAgent) {
        Instant now = clock.instant();
        Exam exam = exams.findById(examId).orElseThrow(() -> AppException.notFound("Exam"));
        if (exam.getStatus() != ExamStatus.PUBLISHED || !exams.isAssignedToStudent(examId, student.getId())) {
            throw AppException.notFound("Exam");
        }
        Optional<Attempt> running = attempts.findFirstByExamIdAndStudentIdAndStatus(examId, student.getId(), AttemptStatus.IN_PROGRESS);
        if (running.isPresent()) {
            Attempt a = running.get();
            if (!now.isAfter(a.getEndsAt().plusSeconds(props.getAttempts().getGraceSeconds()))) {
                return a;
            }
            submitInternal(a.getId(), SubmitReason.TIMEOUT);
        }
        if (now.isBefore(exam.getStartAt())) {
            throw AppException.conflict("This exam has not opened yet.");
        }
        if (!now.isBefore(exam.getEndAt())) {
            throw AppException.gone("This exam is closed.");
        }
        if (!IpMatcher.allowed(exam.getAllowedIps(), ip)) {
            audit.log("EXAM_IP_DENIED", "Exam", examId, "ip " + ip);
            throw AppException.forbidden("This exam can only be taken from the permitted network.");
        }
        if (attempts.countByExamIdAndStudentId(examId, student.getId()) >= exam.getMaxAttempts()) {
            // A parallel start request (double click, second tab) may have created the attempt between the two reads above:
            // resume it instead of reporting that all attempts are used.
            Optional<Attempt> raced = attempts.findFirstByExamIdAndStudentIdAndStatus(examId, student.getId(), AttemptStatus.IN_PROGRESS);
            if (raced.isPresent()) {
                return raced.get();
            }
            throw AppException.conflict("You have used all your attempts for this exam.");
        }
        Integer last = attempts.maxAttemptNo(examId, student.getId());
        Instant endsAt = now.plus(Duration.ofMinutes(exam.getDurationMinutes()));
        if (endsAt.isAfter(exam.getEndAt())) {
            endsAt = exam.getEndAt();
        }
        Attempt a = new Attempt();
        a.setExamId(examId);
        a.setStudentId(student.getId());
        a.setAttemptNo(last == null ? 1 : last + 1);
        a.setStartedAt(now);
        a.setEndsAt(endsAt);
        a.setIpAddress(ip == null ? null : (ip.length() > 64 ? ip.substring(0, 64) : ip));
        a.setUserAgent(userAgent == null ? null : (userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent));
        a.setPlayerSession(newToken());
        a = attempts.saveAndFlush(a);
        buildSnapshot(a, exam);
        audit.logAs(student.getId(), student.getUsername(), "ATTEMPT_START", "Attempt", a.getId(), "exam " + examId);
        return a;
    }

    private void buildSnapshot(Attempt attempt, Exam exam) {
        record Pick(Question q, BigDecimal marks, String voidMode) {
        }
        List<Pick> picks = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        List<ExamQuestion> manual = examQuestions.findByExamIdOrderByPositionAscIdAsc(exam.getId());
        Map<Long, Question> byId = questions.findByIdIn(manual.stream().map(ExamQuestion::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        for (ExamQuestion eq : manual) {
            Question q = byId.get(eq.getQuestionId());
            if (q != null && !"REJECTED".equals(q.getApprovalStatus()) && used.add(q.getId())) {
                picks.add(new Pick(q, eq.getMarks(), eq.getVoidMode()));
            }
        }
        for (ExamQuestionRule r : rules.findByExamIdOrderByIdAsc(exam.getId())) {
            List<Question> pool = questions.findAll(com.mindbridgex.assessment.repository.QuestionSpecs.search(null,
                    exam.getSubject().getId(), r.getCategory() == null ? null : r.getCategory().getId(), null,
                    r.getDifficulty(), null, null)).stream().filter(q -> !used.contains(q.getId()) && !"REJECTED".equals(q.getApprovalStatus())).collect(Collectors.toList());
            Collections.shuffle(pool, RANDOM);
            for (Question q : pool.stream().limit(r.getQuestionCount()).toList()) {
                used.add(q.getId());
                picks.add(new Pick(q, r.getMarksPerQuestion(), "NONE"));
            }
        }
        if (picks.isEmpty()) {
            throw AppException.conflict("This exam has no questions yet. Please contact your teacher.");
        }
        if (exam.isShuffleQuestions()) {
            Collections.shuffle(picks, RANDOM);
        }
        int pos = 1;
        for (Pick p : picks) {
            Question q = p.q();
            AttemptQuestion aq = new AttemptQuestion();
            aq.setAttemptId(attempt.getId());
            aq.setPosition(pos++);
            aq.setSourceQuestionId(q.getId());
            aq.setType(q.getType());
            aq.setText(q.getText());
            aq.setImagePath(q.getImagePath());
            aq.setCoId(q.getCoId());
            aq.setVoidMode(p.voidMode());
            aq.setExplanation(q.getExplanation());
            aq.setMarks(p.marks());
            aq.setNegativeMarks(exam.isNegativeMarking()
                    ? p.marks().multiply(exam.getNegativeFraction()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            aq.setPartialMarking(q.isPartialMarking());
            aq.setCaseSensitive(q.isCaseSensitive());
            aq.setNumericAnswer(q.getNumericAnswer());
            aq.setNumericTolerance(q.getNumericTolerance());
            List<SnapshotOption> opts = new ArrayList<>();
            for (QuestionOption o : q.getOptions()) {
                {
                SnapshotOption so = new SnapshotOption(String.valueOf(o.getId()), o.getText(), o.getMatchText(), o.isCorrect());
                so.setImagePath(o.getImagePath());
                opts.add(so);
            }
            }
            if (exam.isShuffleOptions() && q.getType().isChoice() && q.getType() != QuestionType.TRUE_FALSE) {
                Collections.shuffle(opts, RANDOM);
            }
            aq.setOptions(opts);
            aq = attemptQuestions.save(aq);
        }
        answers.createEmptyAnswers(attempt.getId());
    }

    private static String newToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    // ------------------------------------------------------------------ player

    /** Loads the player. Rotating the session token makes any older tab of the same attempt read-only. */
    @Transactional(noRollbackFor = AppException.class)
    public PlayerView openPlayer(AuthUser student, Long attemptId) {
        Attempt a = owned(student, attemptId);
        Instant now = clock.instant();
        if (a.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw AppException.gone("This attempt has already been submitted.");
        }
        if (now.isAfter(a.getEndsAt().plusSeconds(props.getAttempts().getGraceSeconds()))) {
            submitInternal(a.getId(), SubmitReason.TIMEOUT);
            throw AppException.gone("Time is over. Your answers were submitted automatically.");
        }
        Exam exam = exams.findById(a.getExamId()).orElseThrow(() -> AppException.notFound("Exam"));
        String session = newToken();
        attempts.rotatePlayerSession(a.getId(), session);
        List<AttemptQuestion> qs = attemptQuestions.findByAttemptIdOrderByPositionAsc(a.getId());
        Map<Long, AttemptAnswer> byQ = answers.findByAttemptId(a.getId()).stream()
                .collect(Collectors.toMap(AttemptAnswer::getAttemptQuestionId, Function.identity()));
        List<PlayerQuestion> view = new ArrayList<>();
        int n = 1;
        for (AttemptQuestion q : qs) {
            view.add(toPlayerQuestion(q, byQ.get(q.getId()), n++, false));
        }
        Attempt fresh = attempts.findById(a.getId()).orElseThrow();
        return new PlayerView(fresh, exam, view, session, now.toEpochMilli());
    }

    /** Builds the student-facing question. The answer key is never copied into the view while the attempt is running. */
    PlayerQuestion toPlayerQuestion(AttemptQuestion q, AttemptAnswer a, int number, boolean revealKey) {
        List<PlayerOption> options = new ArrayList<>();
        List<PlayerOption> right = new ArrayList<>();
        if (q.getType() == QuestionType.MATCHING) {
            for (SnapshotOption o : q.getOptions()) {
                options.add(new PlayerOption(o.getKey(), o.getText(), null));
                right.add(new PlayerOption(o.getKey(), o.getMatchText(), null));
            }
            Collections.shuffle(right, new Random(q.getId() * 31L + 17));
        } else if (q.getType().isChoice()) {
            for (SnapshotOption o : q.getOptions()) {
                options.add(new PlayerOption(o.getKey(), o.getText(), o.getImagePath()));
            }
        }
        String answer = a == null ? null : a.getAnswer();
        Set<String> selected = new HashSet<>();
        Map<String, String> pairs = new HashMap<>();
        if (answer != null && !answer.isBlank()) {
            if (q.getType() == QuestionType.MCQ_MULTI) {
                selected = Strategies.parseSet(answer);
            } else if (q.getType() == QuestionType.MATCHING) {
                pairs = Strategies.parseMap(answer);
            } else if (q.getType().isChoice()) {
                selected = Set.of(answer.trim());
            }
        }
        return new PlayerQuestion(q.getId(), number, q.getType(), q.getText(), q.getImagePath(), q.getMarks(), options, right,
                answer, a != null && a.isFlagged(), a != null && a.isAnswered(), selected, pairs);
    }

    // ------------------------------------------------------------------ autosave

    public record SaveResult(int saved, long serverNow, long endsAt) {
    }

    @Transactional(noRollbackFor = AppException.class)
    public SaveResult saveAnswers(AuthUser student, Long attemptId, String playerSession, List<AnswerInput> inputs) {
        Attempt a = owned(student, attemptId);
        Instant now = clock.instant();
        if (a.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw AppException.gone("This attempt has already been submitted.");
        }
        if (playerSession == null || !playerSession.equals(a.getPlayerSession())) {
            throw AppException.conflict("This exam is open in another window. Reload to continue here.");
        }
        if (now.isAfter(a.getEndsAt().plusSeconds(props.getAttempts().getGraceSeconds()))) {
            submitInternal(a.getId(), SubmitReason.TIMEOUT);
            throw AppException.gone("Time is over. Your answers were submitted automatically.");
        }
        if (inputs == null || inputs.size() > 200) {
            throw AppException.bad("Invalid request.");
        }
        if (!AttemptStatus.IN_PROGRESS.name().equals(attempts.lockStatusShared(attemptId))) {
            throw AppException.gone("This attempt has already been submitted.");
        }
        Map<Long, AttemptQuestion> qs = attemptQuestions.findByAttemptIdOrderByPositionAsc(attemptId).stream()
                .collect(Collectors.toMap(AttemptQuestion::getId, Function.identity()));
        int saved = 0;
        int rejected = 0;
        for (AnswerInput in : inputs) {
            AttemptQuestion q = in.questionId() == null ? null : qs.get(in.questionId());
            if (q == null) {
                continue;
            }
            String normalised = normalise(q, in.answer());
            int n = answers.saveAnswer(attemptId, q.getId(), normalised, in.flagged(), now);
            saved += n;
            if (n == 0) {
                rejected++;
            }
        }
        if (rejected > 0) {
            // The write is guarded by the attempt state in SQL: 0 rows means the attempt was closed (and graded) in the
            // meantime, so the client must not be told that the answer was saved.
            throw AppException.gone("This attempt has already been submitted.");
        }
        return new SaveResult(saved, now.toEpochMilli(), a.getEndsAt().toEpochMilli());
    }

    /** Validates and canonicalises an answer for the question type; returns null for "no answer". */
    String normalise(AttemptQuestion q, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.length() > MAX_TEXT) {
            throw AppException.bad("Answer is too long.");
        }
        Set<String> keys = q.getOptions().stream().map(SnapshotOption::getKey).collect(Collectors.toSet());
        switch (q.getType()) {
            case MCQ_SINGLE, TRUE_FALSE -> {
                String k = raw.trim();
                return keys.contains(k) ? k : null;
            }
            case MCQ_MULTI -> {
                Set<String> chosen = Strategies.parseSet(raw);
                chosen.retainAll(keys);
                if (chosen.isEmpty()) {
                    return null;
                }
                return toJson(chosen.stream().sorted().toList());
            }
            case MATCHING -> {
                Map<String, String> given = Strategies.parseMap(raw);
                Map<String, String> clean = new TreeMap<>();
                given.forEach((l, r) -> {
                    if (keys.contains(l) && keys.contains(r)) {
                        clean.put(l, r);
                    }
                });
                return clean.isEmpty() ? null : toJson(clean);
            }
            case NUMERIC -> {
                String t = raw.trim();
                if (t.length() > 40) {
                    throw AppException.bad("Number is too long.");
                }
                return t;
            }
            default -> {
                return raw;
            }
        }
    }

    private static String toJson(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ events

    public record EventResult(boolean submitted, int focusLosses) {
    }

    /** Records a focus-loss event; submits the attempt when the exam's limit is reached. */
    @Transactional
    public EventResult focusLost(AuthUser student, Long attemptId, String playerSession) {
        Attempt a = owned(student, attemptId);
        if (a.getStatus() != AttemptStatus.IN_PROGRESS) {
            return new EventResult(true, a.getFocusLossCount());
        }
        if (playerSession == null || !playerSession.equals(a.getPlayerSession())) {
            throw AppException.conflict("This exam is open in another window.");
        }
        Integer count = attempts.incrementFocusLoss(attemptId);
        int n = count == null ? a.getFocusLossCount() : count;
        Exam exam = exams.findById(a.getExamId()).orElseThrow();
        if (exam.getMaxFocusLosses() > 0 && n >= exam.getMaxFocusLosses()) {
            submitInternal(attemptId, SubmitReason.VIOLATION);
            return new EventResult(true, n);
        }
        return new EventResult(false, n);
    }

    // ------------------------------------------------------------------ submit / expiry

    /** Student presses Submit. Idempotent: submitting twice is not an error. */
    @Transactional(noRollbackFor = AppException.class)
    public Attempt submit(AuthUser student, Long attemptId, String playerSession, List<AnswerInput> finalAnswers) {
        Attempt a = owned(student, attemptId);
        if (a.getStatus() == AttemptStatus.IN_PROGRESS) {
            Instant now = clock.instant();
            boolean inTime = !now.isAfter(a.getEndsAt().plusSeconds(props.getAttempts().getGraceSeconds()));
            if (inTime && finalAnswers != null && playerSession != null && playerSession.equals(a.getPlayerSession())) {
                saveAnswers(student, attemptId, playerSession, finalAnswers);
            }
            submitInternal(attemptId, inTime ? SubmitReason.STUDENT : SubmitReason.TIMEOUT);
        }
        return attempts.findById(attemptId).orElseThrow();
    }

    /** Used by the scheduler and by lazy expiry checks. Exactly one caller wins the status flip and grades. */
    @Transactional
    public boolean submitInternal(Long attemptId, SubmitReason reason) {
        int updated = attempts.markSubmitted(attemptId, clock.instant(), reason.name());
        if (updated == 0) {
            return false;
        }
        grading.tryGrade(attemptId);
        log.debug("Attempt {} submitted ({})", attemptId, reason);
        return true;
    }

    /** Retries grading of submitted attempts that have no grade (a previous grading error). Returns how many were graded. */
    public int regradeUngraded() {
        int done = 0;
        for (Long id : attempts.findSubmittedWithoutGrade(org.springframework.data.domain.PageRequest.of(0, 50))) {
            try {
                Boolean ok = tx.execute(s -> grading.tryGrade(id));
                if (Boolean.TRUE.equals(ok)) {
                    done++;
                }
            } catch (RuntimeException e) {
                log.error("Re-grading of attempt {} failed", id, e);
            }
        }
        return done;
    }

    /** Closes attempts whose time is over (plus grace). Returns how many were closed. */
    public int closeExpired() {
        Instant cutoff = clock.instant().minusSeconds(props.getAttempts().getGraceSeconds());
        List<Long> ids = attempts.findExpiredIds(cutoff,
                org.springframework.data.domain.PageRequest.of(0, props.getAttempts().getMaxAutosubmitBatch()));
        int closed = 0;
        for (Long id : ids) {
            try {
                Boolean ok = tx.execute(s -> submitInternal(id, SubmitReason.TIMEOUT));
                if (Boolean.TRUE.equals(ok)) {
                    closed++;
                }
            } catch (RuntimeException e) {
                log.error("Auto-submit of attempt {} failed", id, e);
            }
        }
        return closed;
    }

    /** Teacher / admin closes every running attempt of an exam. */
    public int closeAllForExam(Long examId) {
        int n = 0;
        for (Long id : attempts.findActiveIdsByExam(examId)) {
            Boolean ok = tx.execute(s -> submitInternal(id, SubmitReason.TEACHER));
            if (Boolean.TRUE.equals(ok)) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ helpers

    @Transactional(readOnly = true)
    public Attempt owned(AuthUser student, Long attemptId) {
        return attempts.findByIdAndStudentId(attemptId, student.getId()).orElseThrow(() -> AppException.notFound("Attempt"));
    }
}
