package com.mindbridgex.assessment.engine;

import com.mindbridgex.assessment.domain.*;
import com.mindbridgex.assessment.entity.*;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.*;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.AttemptService;
import com.mindbridgex.assessment.service.GradingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Examination engine against a real PostgreSQL: scoring rules, duplicate/concurrent requests, the autosave-versus-submit
 * race and the database constraints. Set REQUIRE_DB=true (CI does) to fail instead of skip when the database is missing.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExamEngineIntegrationTest {

    @BeforeAll
    static void requireDatabase() {
        String url = System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5432/assessment_test");
        boolean ok;
        try (Connection c = DriverManager.getConnection(url, System.getenv().getOrDefault("TEST_DB_USERNAME", "assess"), System.getenv().getOrDefault("TEST_DB_PASSWORD", "assess_pw"))) {
            ok = c.isValid(2);
        } catch (Exception e) {
            ok = false;
        }
        if (!ok && "true".equalsIgnoreCase(System.getenv("REQUIRE_DB"))) {
            fail("REQUIRE_DB=true but the PostgreSQL test database is not reachable");
        }
        assumeTrue(ok, "PostgreSQL test database not reachable - skipping");
    }

    @Autowired UserRepository users;
    @Autowired SubjectRepository subjects;
    @Autowired QuestionRepository questions;
    @Autowired ExamRepository exams;
    @Autowired ExamQuestionRepository examQuestions;
    @Autowired ExamAssignmentRepository assignments;
    @Autowired AttemptRepository attempts;
    @Autowired AttemptAnswerRepository answers;
    @Autowired AttemptQuestionRepository attemptQuestions;
    @Autowired GradeRepository grades;
    @Autowired AttemptService attemptService;
    @Autowired GradingService grading;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcClient jdbc;
    @Autowired com.mindbridgex.assessment.service.MarksMatrixService matrixService;

    record Fixture(Exam exam, AuthUser student, List<String> correctTexts, List<String> wrongTexts) {
    }

    private Fixture fixture(int questionCount, String negativeFraction, boolean negative) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User u = new User();
        u.setUsername("eng" + tag);
        u.setFullName("Engine " + tag);
        u.setEmail("eng" + tag + "@example.com");
        u.setPasswordHash(encoder.encode("Test@12345"));
        u.setRoles(EnumSet.of(RoleType.STUDENT));
        u = users.save(u);
        Subject s = new Subject();
        s.setName("Eng " + tag);
        s.setCode("E" + tag);
        s = subjects.save(s);
        Exam e = new Exam();
        e.setSubject(s);
        e.setTitle("Engine exam " + tag);
        e.setDurationMinutes(60);
        e.setStartAt(Instant.now().minusSeconds(120));
        e.setEndAt(Instant.now().plusSeconds(7200));
        e.setMaxAttempts(1);
        e.setPassingMarks(new BigDecimal("1"));
        e.setNegativeMarking(negative);
        e.setNegativeFraction(new BigDecimal(negativeFraction));
        e.setResultPolicy(ResultPolicy.IMMEDIATE);
        e.setStatus(ExamStatus.PUBLISHED);
        e.setShuffleQuestions(false);
        e.setShuffleOptions(false);
        e = exams.save(e);
        List<String> right = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        for (int i = 1; i <= questionCount; i++) {
            Question q = new Question();
            q.setSubject(s);
            q.setType(QuestionType.MCQ_SINGLE);
            q.setDifficulty(Difficulty.EASY);
            q.setText("Q" + i + " " + tag);
            q.setDefaultMarks(BigDecimal.ONE);
            String r = "right" + i + tag;
            String w = "wrong" + i + tag;
            for (int k = 0; k < 2; k++) {
                QuestionOption o = new QuestionOption();
                o.setQuestion(q);
                o.setPosition(k);
                o.setText(k == 0 ? r : w);
                o.setCorrect(k == 0);
                q.getOptions().add(o);
            }
            q = questions.save(q);
            right.add(r);
            wrong.add(w);
            ExamQuestion eq = new ExamQuestion();
            eq.setExamId(e.getId());
            eq.setQuestionId(q.getId());
            eq.setMarks(BigDecimal.ONE);
            eq.setPosition(i);
            examQuestions.save(eq);
        }
        ExamAssignment a = new ExamAssignment();
        a.setExamId(e.getId());
        a.setStudentId(u.getId());
        assignments.save(a);
        return new Fixture(e, new AuthUser(u.getId(), u.getUsername(), u.getFullName(), EnumSet.of(RoleType.STUDENT), false), right, wrong);
    }

    /** Opens the player and answers question i with the chosen option text (null = leave unanswered). */
    private List<AttemptService.AnswerInput> answersFor(AttemptService.PlayerView view, java.util.function.IntFunction<String> choice) {
        List<AttemptService.AnswerInput> list = new ArrayList<>();
        int i = 0;
        for (AttemptService.PlayerQuestion pq : view.questions()) {
            String text = choice.apply(i++);
            if (text == null) {
                continue;
            }
            String key = pq.options().stream().filter(o -> o.text().equals(text)).map(AttemptService.PlayerOption::key).findFirst().orElseThrow();
            list.add(new AttemptService.AnswerInput(pq.id(), key, false));
        }
        return list;
    }

    // ---------------------------------------------------------------- scoring

    @Test
    void sixtyCorrectTwentyWrongTwentyBlankScoresFiftyFiveWithQuarterNegativeMarking() {
        Fixture f = fixture(100, "0.25", true);
        Attempt a = attemptService.startOrResume(f.student(), f.exam().getId(), "127.0.0.1", "JUnit");
        var view = attemptService.openPlayer(f.student(), a.getId());
        var given = answersFor(view, i -> i < 60 ? f.correctTexts().get(i) : i < 80 ? f.wrongTexts().get(i) : null);
        attemptService.submit(f.student(), a.getId(), view.playerSession(), given);
        Grade g = grades.findByAttemptId(a.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("55.00").compareTo(g.getTotalScore()), "60 - 20 x 0.25 = 55");
        assertEquals(0, new BigDecimal("100.00").compareTo(g.getMaxScore()));
        assertEquals(AttemptStatus.GRADED, attempts.findById(a.getId()).orElseThrow().getStatus());
        // 60 right, 20 wrong, 20 blank are recorded individually
        assertEquals(60L, jdbc.sql("select count(*) from attempt_answers where attempt_id = :a and correct = true").param("a", a.getId()).query(Long.class).single());
        assertEquals(20L, jdbc.sql("select count(*) from attempt_answers where attempt_id = :a and answer is not null and correct = false").param("a", a.getId()).query(Long.class).single());
        assertEquals(20L, jdbc.sql("select count(*) from attempt_answers where attempt_id = :a and answer is null").param("a", a.getId()).query(Long.class).single());
    }

    @Test
    void zeroNegativeMarkingAndDecimalMarksAreExact() {
        Fixture f = fixture(10, "0", false);
        Attempt a = attemptService.startOrResume(f.student(), f.exam().getId(), "127.0.0.1", "JUnit");
        var view = attemptService.openPlayer(f.student(), a.getId());
        attemptService.submit(f.student(), a.getId(), view.playerSession(), answersFor(view, i -> i < 3 ? f.correctTexts().get(i) : i < 9 ? f.wrongTexts().get(i) : null));
        assertEquals(0, new BigDecimal("3.00").compareTo(grades.findByAttemptId(a.getId()).orElseThrow().getTotalScore()), "wrong answers cost nothing");

        Fixture d = fixture(4, "0.33", true);
        Attempt b = attemptService.startOrResume(d.student(), d.exam().getId(), "127.0.0.1", "JUnit");
        var v2 = attemptService.openPlayer(d.student(), b.getId());
        attemptService.submit(d.student(), b.getId(), v2.playerSession(), answersFor(v2, i -> i < 2 ? d.correctTexts().get(i) : i == 2 ? d.wrongTexts().get(i) : null));
        assertEquals(0, new BigDecimal("1.67").compareTo(grades.findByAttemptId(b.getId()).orElseThrow().getTotalScore()), "2 - 0.33 = 1.67");
    }

    @Test
    void anEntirelyWrongPaperNeverGoesBelowZeroByDesign() {
        // Existing policy (GradingService.finish floors the total at zero). This test pins it so that a change is deliberate.
        Fixture f = fixture(4, "1", true);
        Attempt a = attemptService.startOrResume(f.student(), f.exam().getId(), "127.0.0.1", "JUnit");
        var view = attemptService.openPlayer(f.student(), a.getId());
        attemptService.submit(f.student(), a.getId(), view.playerSession(), answersFor(view, i -> f.wrongTexts().get(i)));
        assertEquals(0, BigDecimal.ZERO.compareTo(grades.findByAttemptId(a.getId()).orElseThrow().getTotalScore()));
    }

    // ---------------------------------------------------------------- student x question marks sheet

    @Test
    void marksSheetHasEveryStudentOnEveryQuestionAndExportsToExcelAndPdf() throws Exception {
        Fixture f = fixture(10, "0", false);
        Attempt a = attemptService.startOrResume(f.student(), f.exam().getId(), "127.0.0.1", "JUnit");
        var view = attemptService.openPlayer(f.student(), a.getId());
        attemptService.submit(f.student(), a.getId(), view.playerSession(), answersFor(view, i -> i < 3 ? f.correctTexts().get(i) : i < 9 ? f.wrongTexts().get(i) : null));
        AuthUser admin = new AuthUser(f.student().getId(), "adm", "Admin", EnumSet.of(RoleType.ADMIN), false);
        var m = matrixService.build(admin, f.exam().getId());
        assertEquals(10, m.cols().size());
        assertEquals(1, m.students().size());
        var st = m.students().get(0);
        assertEquals(10, st.marks().size(), "one mark per question, blanks as 0");
        assertEquals(0, new BigDecimal("3").compareTo(st.marks().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(0, new BigDecimal("3.00").compareTo(st.total()));
        assertEquals(10, m.qStats().size());
        byte[] xlsx = matrixService.excel(admin, f.exam().getId());
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            assertNotNull(wb.getSheet("Marks matrix"));
            assertNotNull(wb.getSheet("CO attainment"));
            assertNotNull(wb.getSheet("Question key"));
        }
        byte[] pdf = matrixService.pdf(admin, f.exam().getId());
        assertTrue(pdf.length > 500 && new String(pdf, 0, 4).equals("%PDF"));
    }

    // ---------------------------------------------------------------- duplicates and recalculation

    @Test
    void submittingTwiceAndRegradingKeepsOneResultAndTheSameScore() {
        Fixture f = fixture(10, "0.25", true);
        Attempt a = attemptService.startOrResume(f.student(), f.exam().getId(), "127.0.0.1", "JUnit");
        var view = attemptService.openPlayer(f.student(), a.getId());
        var given = answersFor(view, i -> i < 5 ? f.correctTexts().get(i) : null);
        attemptService.submit(f.student(), a.getId(), view.playerSession(), given);
        attemptService.submit(f.student(), a.getId(), view.playerSession(), given);   // double click
        grading.gradeAttempt(a.getId());                                              // recalculation
        grading.gradeAttempt(a.getId());
        assertEquals(1L, jdbc.sql("select count(*) from grades where attempt_id = :a").param("a", a.getId()).query(Long.class).single());
        assertEquals(0, new BigDecimal("5.00").compareTo(grades.findByAttemptId(a.getId()).orElseThrow().getTotalScore()));
    }

    @Test
    void startingTwiceReturnsTheSameRunningAttemptAndTheSnapshotHasOneAnswerRowPerQuestion() {
        Fixture f = fixture(12, "0.25", true);
        Attempt a = attemptService.startOrResume(f.student(), f.exam().getId(), "127.0.0.1", "JUnit");
        Attempt b = attemptService.startOrResume(f.student(), f.exam().getId(), "127.0.0.1", "JUnit");
        assertEquals(a.getId(), b.getId());
        assertEquals(12L, jdbc.sql("select count(*) from attempt_questions where attempt_id = :a").param("a", a.getId()).query(Long.class).single());
        assertEquals(12L, jdbc.sql("select count(*) from attempt_answers where attempt_id = :a").param("a", a.getId()).query(Long.class).single());
    }

    @Test
    void concurrentStartsCreateExactlyOneAttempt() throws Exception {
        Fixture f = fixture(5, "0.25", true);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Long>> futures = new ArrayList<>();
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return attemptService.startOrResume(f.student(), f.exam().getId(), "127.0.0.1", "JUnit").getId();
            }));
        }
        go.countDown();
        Set<Long> ids = new HashSet<>();
        for (Future<Long> fu : futures) {
            ids.add(fu.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertEquals(1, ids.size(), "all eight racing requests must resolve to the same attempt");
        assertEquals(1L, jdbc.sql("select count(*) from attempts where exam_id = :e and student_id = :s").param("e", f.exam().getId()).param("s", f.student().getId()).query(Long.class).single());
    }

    // ---------------------------------------------------------------- deadline

    @Test
    void anAnswerSavedAfterSubmissionIsRefusedAndNeverChangesTheScore() {
        Fixture f = fixture(6, "0.25", true);
        Attempt a = attemptService.startOrResume(f.student(), f.exam().getId(), "127.0.0.1", "JUnit");
        var view = attemptService.openPlayer(f.student(), a.getId());
        attemptService.submit(f.student(), a.getId(), view.playerSession(), answersFor(view, i -> i < 2 ? f.correctTexts().get(i) : null));
        BigDecimal before = grades.findByAttemptId(a.getId()).orElseThrow().getTotalScore();
        var late = answersFor(view, i -> i >= 2 ? f.correctTexts().get(i) : null);
        AppException ex = assertThrows(AppException.class, () -> attemptService.saveAnswers(f.student(), a.getId(), view.playerSession(), late));
        assertEquals(org.springframework.http.HttpStatus.GONE, ex.getStatus());
        assertEquals(0, before.compareTo(grades.findByAttemptId(a.getId()).orElseThrow().getTotalScore()));
        assertEquals(2L, jdbc.sql("select count(*) from attempt_answers where attempt_id = :a and answer is not null").param("a", a.getId()).query(Long.class).single());
    }

    @Test
    void everyAcknowledgedAnswerIsInTheGradeWhenAutosaveRacesTheSubmit() throws Exception {
        for (int round = 0; round < 5; round++) {
            Fixture f = fixture(30, "0.25", true);
            Attempt a = attemptService.startOrResume(f.student(), f.exam().getId(), "127.0.0.1", "JUnit");
            var view = attemptService.openPlayer(f.student(), a.getId());
            List<AttemptService.PlayerQuestion> qs = view.questions();
            ExecutorService pool = Executors.newFixedThreadPool(4);
            AtomicInteger acknowledged = new AtomicInteger();
            Map<Long, String> lastAcked = new ConcurrentHashMap<>();
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> writers = new ArrayList<>();
            for (int t = 0; t < 3; t++) {
                final int off = t;
                writers.add(pool.submit(() -> {
                    go.await();
                    for (int i = off; i < qs.size(); i += 3) {
                        var pq = qs.get(i);
                        String key = pq.options().stream().filter(o -> o.text().equals(f.correctTexts().get(qs.indexOf(pq)))).map(AttemptService.PlayerOption::key).findFirst().orElseThrow();
                        try {
                            attemptService.saveAnswers(f.student(), a.getId(), view.playerSession(), List.of(new AttemptService.AnswerInput(pq.id(), key, false)));
                            acknowledged.incrementAndGet();
                            lastAcked.put(pq.id(), key);
                        } catch (AppException e) {
                            // refused because the attempt was closed: that is the correct outcome for a late write
                        }
                    }
                    return null;
                }));
            }
            Future<?> submitter = pool.submit(() -> {
                go.await();
                Thread.sleep(15);
                attemptService.submit(f.student(), a.getId(), view.playerSession(), null);
                return null;
            });
            go.countDown();
            for (Future<?> w : writers) {
                w.get(60, TimeUnit.SECONDS);
            }
            submitter.get(60, TimeUnit.SECONDS);
            pool.shutdown();
            Grade g = grades.findByAttemptId(a.getId()).orElseThrow();
            // every acknowledged answer is stored, and the stored answers produce exactly the recorded grade
            long stored = jdbc.sql("select count(*) from attempt_answers where attempt_id = :a and answer is not null").param("a", a.getId()).query(Long.class).single();
            assertEquals(acknowledged.get(), stored, "acknowledged answers must equal stored answers (round " + round + ")");
            grading.gradeAttempt(a.getId());
            assertEquals(0, g.getTotalScore().compareTo(grades.findByAttemptId(a.getId()).orElseThrow().getTotalScore()),
                    "a re-grade must not change the score: no answer may land after grading (round " + round + ")");
            assertEquals(0, new BigDecimal(acknowledged.get()).setScale(2).compareTo(g.getTotalScore()), "30 correct answers, +1 each");
        }
    }

    @Test
    void aGradingFailureKeepsTheSubmissionAndTheSchedulerGradesItLater() {
        Fixture f = fixture(4, "0.25", true);
        Attempt a = attemptService.startOrResume(f.student(), f.exam().getId(), "127.0.0.1", "JUnit");
        var view = attemptService.openPlayer(f.student(), a.getId());
        var given = answersFor(view, i -> f.correctTexts().get(i));
        attemptService.saveAnswers(f.student(), a.getId(), view.playerSession(), given);
        // Corrupt the snapshot so that grading throws (options JSON is no longer a list of options)
        jdbc.sql("update attempt_questions set options = '{\"broken\":true}'::jsonb where attempt_id = :a and position = 1").param("a", a.getId()).update();
        attemptService.submit(f.student(), a.getId(), view.playerSession(), null);
        Attempt after = attempts.findById(a.getId()).orElseThrow();
        assertEquals(AttemptStatus.SUBMITTED, after.getStatus(), "the submission itself must survive a grading error");
        assertTrue(grades.findByAttemptId(a.getId()).isEmpty());
        // repair the data; the scheduler's retry must now produce the grade
        jdbc.sql("update attempt_questions set options = '[]'::jsonb where attempt_id = :a and position = 1").param("a", a.getId()).update();
        assertTrue(attemptService.regradeUngraded() >= 1);
        assertTrue(grades.findByAttemptId(a.getId()).isPresent());
    }

    // ---------------------------------------------------------------- database constraints

    @Test
    void approvalStatusConstraintAcceptsDraftAndTheOtherWorkflowStatesAndRejectsGarbage() {
        Fixture f = fixture(1, "0.25", true);
        for (String st : List.of("DRAFT", "PENDING", "APPROVED", "REJECTED")) {
            assertEquals(1, jdbc.sql("update exams set approval_status = :s where id = :id").param("s", st).param("id", f.exam().getId()).update());
        }
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.sql("update exams set approval_status = 'BOGUS' where id = :id").param("id", f.exam().getId()).update());
    }

    @Test
    void theDatabaseItselfRefusesASecondRunningAttemptAndASecondGrade() {
        Fixture f = fixture(2, "0.25", true);
        Attempt a = attemptService.startOrResume(f.student(), f.exam().getId(), "127.0.0.1", "JUnit");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql(
                "insert into attempts (exam_id, student_id, attempt_no, status, started_at, ends_at, player_session, version, focus_loss_count, created_at, updated_at) "
                        + "values (:e, :s, 99, 'IN_PROGRESS', now(), now() + interval '1 hour', 'x', 0, 0, now(), now())")
                .param("e", f.exam().getId()).param("s", f.student().getId()).update());
        var view = attemptService.openPlayer(f.student(), a.getId());
        attemptService.submit(f.student(), a.getId(), view.playerSession(), null);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql(
                "insert into grades (attempt_id, total_score, max_score, percentage, passed, pending_manual, graded_at) values (:a, 1, 2, 50, true, 0, now())")
                .param("a", a.getId()).update());
    }
}
