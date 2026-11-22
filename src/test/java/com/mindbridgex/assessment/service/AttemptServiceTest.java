package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.domain.*;
import com.mindbridgex.assessment.entity.*;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.*;
import com.mindbridgex.assessment.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Timer, grace period, session and ownership rules of {@link AttemptService}. The database is mocked. */
class AttemptServiceTest {

    static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    AttemptRepository attempts = mock(AttemptRepository.class);
    AttemptQuestionRepository attemptQuestions = mock(AttemptQuestionRepository.class);
    AttemptAnswerRepository answers = mock(AttemptAnswerRepository.class);
    ExamRepository exams = mock(ExamRepository.class);
    ExamQuestionRepository examQuestions = mock(ExamQuestionRepository.class);
    ExamQuestionRuleRepository rules = mock(ExamQuestionRuleRepository.class);
    QuestionRepository questions = mock(QuestionRepository.class);
    GradingService grading = mock(GradingService.class);
    AuditService audit = mock(AuditService.class);
    AppProperties props = new AppProperties();
    AuthUser student = new AuthUser(7L, "stud", "Stud Ent", EnumSet.of(RoleType.STUDENT), false);
    Exam exam;
    Clock clock;
    AttemptService service;

    @BeforeEach
    void setUp() {
        props.getAttempts().setGraceSeconds(10);
        exam = new Exam();
        Subject subject = new Subject();
        subject.setName("S");
        exam.setSubject(subject);
        exam.setStatus(ExamStatus.PUBLISHED);
        exam.setDurationMinutes(60);
        exam.setMaxAttempts(2);
        exam.setStartAt(NOW.minusSeconds(600));
        exam.setEndAt(NOW.plusSeconds(7200));
        exam.setShuffleQuestions(false);
        exam.setShuffleOptions(false);
        setId(exam, 5L);
        useClock(NOW);
    }

    private void useClock(Instant at) {
        clock = Clock.fixed(at, ZoneOffset.UTC);
        PlatformTransactionManager tm = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition d) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus s) {
            }

            @Override
            public void rollback(TransactionStatus s) {
            }
        };
        service = new AttemptService(attempts, attemptQuestions, answers, exams, examQuestions, rules, questions, grading,
                audit, props, clock, tm);
    }

    private static void setId(Object entity, Long id) {
        try {
            var f = com.mindbridgex.assessment.entity.BaseEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubStartDependencies() {
        when(exams.findById(5L)).thenReturn(Optional.of(exam));
        when(exams.isAssignedToStudent(5L, 7L)).thenReturn(true);
        when(attempts.findFirstByExamIdAndStudentIdAndStatus(5L, 7L, AttemptStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(attempts.countByExamIdAndStudentId(5L, 7L)).thenReturn(0L);
        when(attempts.maxAttemptNo(5L, 7L)).thenReturn(null);
        when(attempts.saveAndFlush(any(Attempt.class))).thenAnswer(i -> {
            Attempt a = i.getArgument(0);
            setId(a, 100L);
            return a;
        });
        ExamQuestion eq = new ExamQuestion();
        eq.setExamId(5L);
        eq.setQuestionId(11L);
        eq.setMarks(new BigDecimal("2"));
        when(examQuestions.findByExamIdOrderByPositionAscIdAsc(5L)).thenReturn(List.of(eq));
        Question q = new Question();
        setId(q, 11L);
        q.setType(QuestionType.MCQ_SINGLE);
        q.setText("Q?");
        q.setDefaultMarks(new BigDecimal("2"));
        QuestionOption a = new QuestionOption();
        a.setText("A");
        a.setCorrect(true);
        q.getOptions().add(a);
        when(questions.findByIdIn(any())).thenReturn(List.of(q));
        when(rules.findByExamIdOrderByIdAsc(5L)).thenReturn(List.of());
        when(attemptQuestions.save(any(AttemptQuestion.class))).thenAnswer(i -> {
            AttemptQuestion aq = i.getArgument(0);
            aq.setId(500L);
            return aq;
        });
    }

    @Test
    void attemptEndsAfterTheConfiguredDuration() {
        stubStartDependencies();
        Attempt a = service.startOrResume(student, 5L, "127.0.0.1", "JUnit");
        assertEquals(NOW, a.getStartedAt());
        assertEquals(NOW.plusSeconds(3600), a.getEndsAt());
        assertEquals(1, a.getAttemptNo());
        assertNotNull(a.getPlayerSession());
    }

    @Test
    void attemptNeverOutlivesTheExamWindow() {
        stubStartDependencies();
        exam.setEndAt(NOW.plusSeconds(900));   // window closes in 15 minutes, duration is 60
        Attempt a = service.startOrResume(student, 5L, "127.0.0.1", "JUnit");
        assertEquals(NOW.plusSeconds(900), a.getEndsAt());
    }

    @Test
    void snapshotFreezesQuestionAndAnswerKey() {
        stubStartDependencies();
        service.startOrResume(student, 5L, "127.0.0.1", "JUnit");
        ArgumentCaptor<AttemptQuestion> cap = ArgumentCaptor.forClass(AttemptQuestion.class);
        verify(attemptQuestions).save(cap.capture());
        AttemptQuestion aq = cap.getValue();
        assertEquals("Q?", aq.getText());
        assertEquals(new BigDecimal("2"), aq.getMarks());
        assertEquals(1, aq.getOptions().size());
        assertTrue(aq.getOptions().get(0).isCorrect());
        verify(answers).createEmptyAnswers(any());   // empty answer rows exist from the start (one set-based INSERT)
    }

    @Test
    void negativeMarksAreDerivedFromTheExamFraction() {
        stubStartDependencies();
        exam.setNegativeMarking(true);
        exam.setNegativeFraction(new BigDecimal("0.25"));
        service.startOrResume(student, 5L, "127.0.0.1", "JUnit");
        ArgumentCaptor<AttemptQuestion> cap = ArgumentCaptor.forClass(AttemptQuestion.class);
        verify(attemptQuestions).save(cap.capture());
        assertEquals(0, new BigDecimal("0.50").compareTo(cap.getValue().getNegativeMarks()));
    }

    @Test
    void cannotStartBeforeOpeningOrAfterClosing() {
        stubStartDependencies();
        exam.setStartAt(NOW.plusSeconds(60));
        AppException e = assertThrows(AppException.class, () -> service.startOrResume(student, 5L, "1.1.1.1", "x"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        exam.setStartAt(NOW.minusSeconds(600));
        exam.setEndAt(NOW);
        e = assertThrows(AppException.class, () -> service.startOrResume(student, 5L, "1.1.1.1", "x"));
        assertEquals(HttpStatus.GONE, e.getStatus());
    }

    @Test
    void cannotStartWhenAttemptsAreExhausted() {
        stubStartDependencies();
        when(attempts.countByExamIdAndStudentId(5L, 7L)).thenReturn(2L);
        AppException e = assertThrows(AppException.class, () -> service.startOrResume(student, 5L, "1.1.1.1", "x"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(attempts, never()).saveAndFlush(any());
    }

    @Test
    void ipRestrictionIsEnforced() {
        stubStartDependencies();
        exam.setAllowedIps("10.0.0.0/8");
        AppException e = assertThrows(AppException.class, () -> service.startOrResume(student, 5L, "8.8.8.8", "x"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertDoesNotThrow(() -> service.startOrResume(student, 5L, "10.1.2.3", "x"));
    }

    @Test
    void unassignedStudentSeesNotFound() {
        stubStartDependencies();
        when(exams.isAssignedToStudent(5L, 7L)).thenReturn(false);
        AppException e = assertThrows(AppException.class, () -> service.startOrResume(student, 5L, "1.1.1.1", "x"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void runningAttemptIsResumedWithTheOriginalTimer() {
        stubStartDependencies();
        Attempt running = running(NOW.plusSeconds(1200));
        when(attempts.findFirstByExamIdAndStudentIdAndStatus(5L, 7L, AttemptStatus.IN_PROGRESS)).thenReturn(Optional.of(running));
        Attempt a = service.startOrResume(student, 5L, "1.1.1.1", "x");
        assertSame(running, a);
        verify(attempts, never()).saveAndFlush(any());
    }

    private Attempt running(Instant endsAt) {
        Attempt a = new Attempt();
        setId(a, 100L);
        a.setExamId(5L);
        a.setStudentId(7L);
        a.setStatus(AttemptStatus.IN_PROGRESS);
        a.setStartedAt(NOW.minusSeconds(2400));
        a.setEndsAt(endsAt);
        a.setPlayerSession("sess");
        when(attempts.findByIdAndStudentId(100L, 7L)).thenReturn(Optional.of(a));
        when(attempts.lockStatusShared(100L)).thenReturn("IN_PROGRESS");
        return a;
    }

    @Test
    void answersAreAcceptedWhileTimeRemains() {
        running(NOW.plusSeconds(60));
        AttemptQuestion q = new AttemptQuestion();
        q.setId(500L);
        q.setType(QuestionType.NUMERIC);
        when(attemptQuestions.findByAttemptIdOrderByPositionAsc(100L)).thenReturn(List.of(q));
        when(answers.saveAnswer(eq(100L), eq(500L), eq("42"), eq(true), any())).thenReturn(1);
        var r = service.saveAnswers(student, 100L, "sess", List.of(new AttemptService.AnswerInput(500L, "42", true)));
        assertEquals(1, r.saved());
        assertEquals(NOW.toEpochMilli(), r.serverNow());
    }

    @Test
    void answersWithinTheGracePeriodAreStillAccepted() {
        running(NOW.minusSeconds(5));   // 5 seconds late, grace is 10
        when(attemptQuestions.findByAttemptIdOrderByPositionAsc(100L)).thenReturn(List.of());
        assertDoesNotThrow(() -> service.saveAnswers(student, 100L, "sess", List.of()));
        verify(attempts, never()).markSubmitted(anyLong(), any(), anyString());
    }

    @Test
    void answersAfterTheGracePeriodAreRejectedAndTheAttemptIsClosed() {
        running(NOW.minusSeconds(11));
        when(attempts.markSubmitted(eq(100L), any(), eq("TIMEOUT"))).thenReturn(1);
        AppException e = assertThrows(AppException.class, () -> service.saveAnswers(student, 100L, "sess", List.of()));
        assertEquals(HttpStatus.GONE, e.getStatus());
        verify(attempts).markSubmitted(eq(100L), eq(NOW), eq("TIMEOUT"));
        verify(grading).tryGrade(100L);
        verify(answers, never()).saveAnswer(anyLong(), anyLong(), any(), anyBoolean(), any());
    }

    @Test
    void anotherWindowWithADifferentSessionTokenIsRejected() {
        running(NOW.plusSeconds(600));
        AppException e = assertThrows(AppException.class, () -> service.saveAnswers(student, 100L, "other-tab", List.of()));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void aStudentCannotTouchSomeoneElsesAttempt() {
        running(NOW.plusSeconds(600));
        AuthUser other = new AuthUser(8L, "o", "O", EnumSet.of(RoleType.STUDENT), false);
        when(attempts.findByIdAndStudentId(100L, 8L)).thenReturn(Optional.empty());
        AppException e = assertThrows(AppException.class, () -> service.saveAnswers(other, 100L, "sess", List.of()));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void submitGradesExactlyOnceEvenWhenCalledTwice() {
        Attempt a = running(NOW.plusSeconds(600));
        when(attempts.findById(100L)).thenReturn(Optional.of(a));
        when(attempts.markSubmitted(eq(100L), any(), anyString())).thenReturn(1).thenReturn(0);
        service.submit(student, 100L, "sess", null);
        a.setStatus(AttemptStatus.SUBMITTED);
        service.submit(student, 100L, "sess", null);
        verify(grading, times(1)).tryGrade(100L);
    }

    @Test
    void lateSubmitIsRecordedAsTimeout() {
        Attempt a = running(NOW.minusSeconds(60));
        when(attempts.findById(100L)).thenReturn(Optional.of(a));
        when(attempts.markSubmitted(eq(100L), any(), anyString())).thenReturn(1);
        service.submit(student, 100L, "sess", List.of(new AttemptService.AnswerInput(1L, "late answer", false)));
        verify(attempts).markSubmitted(eq(100L), eq(NOW), eq("TIMEOUT"));
        verify(answers, never()).saveAnswer(anyLong(), anyLong(), any(), anyBoolean(), any());
    }

    @Test
    void focusLossBeyondTheLimitSubmitsTheAttempt() {
        running(NOW.plusSeconds(600));
        exam.setMaxFocusLosses(3);
        when(exams.findById(5L)).thenReturn(Optional.of(exam));
        when(attempts.incrementFocusLoss(100L)).thenReturn(2).thenReturn(3);
        when(attempts.markSubmitted(eq(100L), any(), eq("VIOLATION"))).thenReturn(1);
        assertFalse(service.focusLost(student, 100L, "sess").submitted());
        assertTrue(service.focusLost(student, 100L, "sess").submitted());
        verify(grading).tryGrade(100L);
    }

    @Test
    void schedulerClosesExpiredAttempts() {
        when(attempts.findExpiredIds(any(), any())).thenReturn(List.of(1L, 2L));
        AtomicLong n = new AtomicLong();
        when(attempts.markSubmitted(anyLong(), any(), eq("TIMEOUT"))).thenAnswer(i -> {
            n.incrementAndGet();
            return 1;
        });
        assertEquals(2, service.closeExpired());
        assertEquals(2, n.get());
        // the cut-off is "now minus grace"
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(attempts).findExpiredIds(cutoff.capture(), any());
        assertEquals(NOW.minusSeconds(10), cutoff.getValue());
    }

    @Test
    void anAnswerIsNeverAcknowledgedAfterTheAttemptWasClosedMeanwhile() {
        running(NOW.plusSeconds(60));
        AttemptQuestion q = new AttemptQuestion();
        q.setId(500L);
        q.setType(QuestionType.NUMERIC);
        when(attemptQuestions.findByAttemptIdOrderByPositionAsc(100L)).thenReturn(List.of(q));
        // the SQL guard found the attempt already submitted: 0 rows changed
        when(answers.saveAnswer(eq(100L), eq(500L), any(), anyBoolean(), any())).thenReturn(0);
        AppException e = assertThrows(AppException.class,
                () -> service.saveAnswers(student, 100L, "sess", List.of(new AttemptService.AnswerInput(500L, "42", false))));
        assertEquals(HttpStatus.GONE, e.getStatus());
    }

    @Test
    void saveIsRefusedWhenTheRowLockShowsTheAttemptIsNoLongerInProgress() {
        running(NOW.plusSeconds(60));
        when(attempts.lockStatusShared(100L)).thenReturn("SUBMITTED");
        AppException e = assertThrows(AppException.class,
                () -> service.saveAnswers(student, 100L, "sess", List.of(new AttemptService.AnswerInput(500L, "42", false))));
        assertEquals(HttpStatus.GONE, e.getStatus());
        verify(answers, never()).saveAnswer(anyLong(), anyLong(), any(), anyBoolean(), any());
    }

    @Test
    void ungradedSubmittedAttemptsAreRetriedByTheScheduler() {
        when(attempts.findSubmittedWithoutGrade(any())).thenReturn(List.of(11L, 12L));
        when(grading.tryGrade(11L)).thenReturn(true);
        when(grading.tryGrade(12L)).thenReturn(false);   // still failing: reported as not graded, no exception escapes
        assertEquals(1, service.regradeUngraded());
        verify(grading).tryGrade(11L);
        verify(grading).tryGrade(12L);
    }
}
