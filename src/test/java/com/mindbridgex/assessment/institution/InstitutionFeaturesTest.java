package com.mindbridgex.assessment.institution;

import com.mindbridgex.assessment.domain.*;
import com.mindbridgex.assessment.dto.OptionForm;
import com.mindbridgex.assessment.dto.QuestionForm;
import com.mindbridgex.assessment.entity.*;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.*;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.security.CookieUtil;
import com.mindbridgex.assessment.security.JwtService;
import com.mindbridgex.assessment.security.UserLookupService;
import com.mindbridgex.assessment.service.*;
import com.mindbridgex.assessment.util.ContentHasher;
import jakarta.servlet.http.Cookie;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Super Admin, duplicate detection, DOCX upload, live correction and CO-PO attainment against a real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InstitutionFeaturesTest {

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
            org.junit.jupiter.api.Assertions.fail("REQUIRE_DB=true but the PostgreSQL test database is not reachable");
        }
        assumeTrue(ok, "PostgreSQL test database not reachable - skipping");
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SubjectRepository subjects;
    @Autowired ExamRepository exams;
    @Autowired ExamQuestionRepository examQuestions;
    @Autowired QuestionRepository questions;
    @Autowired AttemptRepository attempts;
    @Autowired AttemptQuestionRepository attemptQuestions;
    @Autowired AttemptAnswerRepository answers;
    @Autowired GradeRepository grades;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired UserLookupService lookup;
    @Autowired QuestionService questionService;
    @Autowired ExamUploadService upload;
    @Autowired LiveCorrectionService live;
    @Autowired OverrideService overrides;
    @Autowired DuplicateService duplicates;
    @Autowired OutcomeReportService outcomeReports;
    @Autowired CoPoService copo;
    @Autowired JdbcClient jdbc;

    User superUser, admin, teacher, student;
    AuthUser superAuth, teacherAuth;
    Subject subject;
    Exam exam;

    @BeforeEach
    void data() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        superUser = user("sup" + tag, RoleType.SUPER_ADMIN);
        admin = user("adm" + tag, RoleType.ADMIN);
        teacher = user("tea" + tag, RoleType.TEACHER);
        student = user("stu" + tag, RoleType.STUDENT);
        subject = new Subject();
        subject.setName("Subj " + tag);
        subject.setCode("S" + tag);
        subject.getTeacherIds().add(teacher.getId());
        subject = subjects.save(subject);
        exam = new Exam();
        exam.setSubject(subject);
        exam.setTitle("Exam " + tag);
        exam.setStartAt(Instant.now().minusSeconds(60));
        exam.setEndAt(Instant.now().plusSeconds(3600));
        exam = exams.save(exam);
        superAuth = new AuthUser(superUser.getId(), superUser.getUsername(), "Super", EnumSet.of(RoleType.SUPER_ADMIN), false);
        teacherAuth = new AuthUser(teacher.getId(), teacher.getUsername(), "Teacher", EnumSet.of(RoleType.TEACHER), false);
        lookup.evictAll();
    }

    private User user(String name, RoleType role) {
        User u = new User();
        u.setUsername(name);
        u.setFullName(name);
        u.setEmail(name + "@example.com");
        u.setPasswordHash(encoder.encode("Test@12345"));
        u.setRoles(EnumSet.of(role));
        return users.save(u);
    }

    private RequestPostProcessor as(User u) {
        return req -> {
            req.setCookies(new Cookie(CookieUtil.ACCESS_COOKIE, jwt.createAccessToken(u)));
            return req;
        };
    }

    private QuestionForm mcq(String text, String... opts) {
        QuestionForm f = new QuestionForm();
        f.setSubjectId(subject.getId());
        f.setType(QuestionType.MCQ_SINGLE);
        f.setText(text);
        for (int i = 0; i < opts.length; i++) {
            f.getOptions().add(new OptionForm(null, opts[i], null, i == 0));
        }
        return f;
    }

    // ------------------------------------------------------------------ roles

    @Test
    void superAdminHasEveryAreaAndOthersCannotEnterTheSuperArea() throws Exception {
        mvc.perform(get("/super").with(as(superUser))).andExpect(status().isOk());
        mvc.perform(get("/super/branches").with(as(superUser))).andExpect(status().isOk());
        mvc.perform(get("/super/live").with(as(superUser))).andExpect(status().isOk());
        mvc.perform(get("/super/overrides").with(as(superUser))).andExpect(status().isOk());
        mvc.perform(get("/super/outcomes").with(as(superUser))).andExpect(status().isOk());
        mvc.perform(get("/admin/duplicates").with(as(superUser))).andExpect(status().isOk());
        mvc.perform(get("/admin/users").with(as(superUser))).andExpect(status().isOk());
        mvc.perform(get("/teacher/exams").with(as(superUser))).andExpect(status().isOk());
        mvc.perform(get("/reports/outcomes").with(as(superUser))).andExpect(status().isOk());
        mvc.perform(get("/super").with(as(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/super/live").with(as(teacher))).andExpect(status().isForbidden());
        mvc.perform(get("/super").with(as(student))).andExpect(status().isForbidden());
        mvc.perform(get("/reports/outcomes").with(as(teacher))).andExpect(status().isOk());
    }

    @Test
    void onlyASuperAdminMayOverride() {
        assertThrows(AppException.class, () -> overrides.examApproval(teacherAuth, exam.getId(), false, "not allowed"));
        overrides.examApproval(superAuth, exam.getId(), false, "Wrong syllabus");
        assertEquals("REJECTED", exams.findById(exam.getId()).orElseThrow().getApprovalStatus());
        assertThrows(AppException.class, () -> overrides.examApproval(superAuth, exam.getId(), true, "x"), "a reason is required");
        long logged = jdbc.sql("select count(*) from audit_logs where action = 'OVERRIDE_EXAM_REJECT' and entity_id = :i").param("i", String.valueOf(exam.getId())).query(Long.class).single();
        assertEquals(1, logged);
    }

    // ------------------------------------------------------------------ duplicates

    @Test
    void sameQuestionWithDifferentCaseAndPunctuationIsADuplicate() {
        questionService.save(teacherAuth, mcq("What is 2 + 2?", "4", "5", "3"));
        AppException e = assertThrows(AppException.class, () -> questionService.save(teacherAuth, mcq("what is 2+2", "4.", "5", "3")));
        assertTrue(e instanceof QuestionService.DuplicateQuestionException);
        QuestionForm allowed = mcq("What is 2 + 2?", "4", "5", "3");
        allowed.setAllowDuplicate(true);
        assertNotNull(questionService.save(teacherAuth, allowed).getId());
        assertTrue(duplicates.groups().stream().anyMatch(g -> g.members().size() == 2 && g.subject().equals(subject.getName())));
    }

    @Test
    void mergingDuplicatesKeepsExamsAndPastAnswersWorking() {
        Question a = questionService.save(teacherAuth, mcq("Capital of India?", "New Delhi", "Mumbai"));
        QuestionForm f = mcq("Capital of India?", "New Delhi", "Mumbai");
        f.setAllowDuplicate(true);
        Question b = questionService.save(teacherAuth, f);
        ExamQuestion eq = new ExamQuestion();
        eq.setExamId(exam.getId());
        eq.setQuestionId(b.getId());
        eq.setMarks(BigDecimal.ONE);
        eq.setPosition(1);
        examQuestions.save(eq);
        assertEquals(1, duplicates.merge(a.getId(), List.of(b.getId())));
        assertEquals(a.getId(), examQuestions.findById(eq.getId()).orElseThrow().getQuestionId());
        assertTrue(questions.findById(b.getId()).orElseThrow().isArchived());
    }

    @Test
    void hashIgnoresCaseAccentsPunctuationAndOptionOrder() {
        String h1 = ContentHasher.contentHash("MCQ_SINGLE", "What is  2+2 ?", List.of("Four", "Five"));
        String h2 = ContentHasher.contentHash("MCQ_SINGLE", "what is 2+2", List.of("five!", "FOUR"));
        assertEquals(h1, h2);
        assertNotEquals(h1, ContentHasher.contentHash("MCQ_SINGLE", "What is 2+3?", List.of("Four", "Five")));
    }

    // ------------------------------------------------------------------ DOCX upload

    private static byte[] png(int shade) throws Exception {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 30; y++) {
                img.setRGB(x, y, (shade * 40) << 16 | (x * 6) << 8 | y);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static void put(XWPFTable t, int row, int col, String text) {
        t.getRow(row).getCell(col).setText(text);
    }

    private static void picture(XWPFTable t, int row, int col, byte[] png) throws Exception {
        XWPFRun r = t.getRow(row).getCell(col).getParagraphs().get(0).createRun();
        r.addPicture(new ByteArrayInputStream(png), org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG, "p.png", org.apache.poi.util.Units.toEMU(40), org.apache.poi.util.Units.toEMU(30));
    }

    private byte[] sampleDocx(Long coUnknown) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String[] head = {"Q No", "Question", "Option A", "Option B", "Option C", "Option D", "Correct", "Marks", "CO", "Difficulty"};
            XWPFTable t = doc.createTable(6, head.length);
            for (int i = 0; i < head.length; i++) {
                put(t, 0, i, head[i]);
            }
            String[][] rows = {
                    {"1", "Capital of India?", "Mumbai", "New Delhi", "Kolkata", "Chennai", "B", "2", "CO1", "Easy"},
                    {"2", "capital of india", "Mumbai.", "New Delhi", "Kolkata", "Chennai", "B", "", "CO1", ""},
                    {"3", "Missing correct answer", "x", "y", "", "", "", "", "CO1", ""},
                    {"4", "Primes?", "2", "4", "5", "9", "A,C", "3", "CO1", "Hard"},
                    {"5", "", "", "", "", "", "A", "", "CO1", ""},
            };
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < head.length; c++) {
                    put(t, r + 1, c, rows[r][c]);
                }
            }
            // row 5 (table row index 5): a picture question whose options are pictures only
            put(t, 5, 0, "5");
            picture(t, 5, 1, png(1));
            picture(t, 5, 2, png(2));
            picture(t, 5, 3, png(3));
            put(t, 5, 6, "B");
            put(t, 5, 8, "CO1");
            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void docxUploadStagesReviewsAndOnlySavesAfterConfirmation() throws Exception {
        jdbc.sql("insert into course_outcomes (subject_id, code, target_percent) values (:s, 'CO1', 60)").param("s", subject.getId()).update();
        MockMultipartFile file = new MockMultipartFile("file", "paper.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", sampleDocx(null));
        Long sid = upload.upload(teacherAuth, exam.getId(), file);
        ExamUploadService.Session s = upload.view(teacherAuth, sid);
        assertEquals(5, s.uploaded());
        assertEquals(3, s.valid(), "rows 2(1), 5(4), 6(picture) are valid");
        assertEquals(1, s.duplicates(), "row 3 repeats row 2 of the file");
        assertEquals(1, s.failed(), "row 4 has no correct answer");
        ExamUploadService.Row bad = s.rows().stream().filter(r -> r.status().equals("ERROR")).findFirst().orElseThrow();
        assertEquals(4, bad.rowNo());
        assertTrue(bad.message().toLowerCase().contains("correct"));
        ExamUploadService.Row pic = s.rows().get(4);
        assertEquals("VALID", pic.status());
        assertNotNull(pic.item().image(), "question picture extracted");
        assertNotNull(pic.item().options().get(0).image());
        assertEquals(0, examQuestions.findByExamIdOrderByPositionAscIdAsc(exam.getId()).size(), "nothing saved before confirm");

        ExamUploadService.ConfirmResult r = upload.confirm(teacherAuth, sid);
        assertEquals(3, r.created());
        assertEquals(2, r.skipped(), "the duplicate and the failed row are skipped");
        assertEquals(3, examQuestions.findByExamIdOrderByPositionAscIdAsc(exam.getId()).size());
        List<Question> saved = questions.findAll().stream().filter(q -> q.getSubject().getId().equals(subject.getId())).toList();
        assertEquals(3, saved.size());
        assertTrue(saved.stream().allMatch(q -> q.getSource() != null && q.getSource().startsWith("bulk upload: paper.docx")));
        Question picQ = saved.stream().filter(q -> q.getImagePath() != null).findFirst().orElseThrow();
        assertEquals(2L, jdbc.sql("select count(*) from question_options where question_id = :q and image_path is not null").param("q", picQ.getId()).query(Long.class).single());
        assertEquals(1L, jdbc.sql("select count(*) from question_options where question_id = :q and correct").param("q", picQ.getId()).query(Long.class).single());
        Question multi = saved.stream().filter(q -> q.getType() == QuestionType.MCQ_MULTI).findFirst().orElseThrow();
        assertEquals(2L, jdbc.sql("select count(*) from question_options where question_id = :q and correct").param("q", multi.getId()).query(Long.class).single());
        assertEquals(new BigDecimal("3.00"), multi.getDefaultMarks().setScale(2));
        assertThrows(AppException.class, () -> upload.confirm(teacherAuth, sid), "cannot confirm twice");
    }

    @Test
    void duplicateAgainstTheBankIsFlaggedAndCanBeKept() throws Exception {
        questionService.save(teacherAuth, mcq("Capital of India?", "New Delhi", "Mumbai", "Kolkata", "Chennai"));
        jdbc.sql("insert into course_outcomes (subject_id, code, target_percent) values (:s, 'CO1', 60)").param("s", subject.getId()).update();
        MockMultipartFile file = new MockMultipartFile("file", "p.docx", "application/octet-stream", sampleDocx(null));
        Long sid = upload.upload(teacherAuth, exam.getId(), file);
        ExamUploadService.Session s = upload.view(teacherAuth, sid);
        // the bank copy has the correct answer first; the file has B - answers are part of the question only through the options, so it is a duplicate
        ExamUploadService.Row first = s.rows().get(0);
        assertEquals("DUPLICATE", first.status());
        assertTrue(first.message().contains("Question Bank"));
        upload.setKeep(teacherAuth, sid, first.id(), true);
        assertEquals(3, upload.confirm(teacherAuth, sid).created(), "2 valid + the duplicate the teacher chose to keep; the in-file copy stays skipped");
    }

    @Test
    void templateIsADocxThatTheParserAccepts() {
        byte[] t = new DocxQuestionParser(null).template();
        assertTrue(t.length > 1000);
        List<DocxQuestionParser.ParsedRow> rows = new DocxQuestionParser(null).parse(new ByteArrayInputStream(t));
        assertEquals(3, rows.size());
        assertEquals("B", rows.get(0).correct());
    }

    @Test
    void correctColumnAcceptsLettersInManyWrittenForms() {
        assertEquals(List.of(1), ExamUploadService.correctIndexes("b"));
        assertEquals(List.of(0, 2), ExamUploadService.correctIndexes("A, C"));
        assertEquals(List.of(0, 2), ExamUploadService.correctIndexes("AC"));
        assertTrue(ExamUploadService.correctIndexes("G").isEmpty());
        assertTrue(ExamUploadService.correctIndexes("").isEmpty());
        assertTrue(ExamUploadService.correctIndexes("none").isEmpty());
    }

    // ------------------------------------------------------------------ live correction and CO-PO

    private Attempt submittedAttempt(Question q, String chosenKeyOfOptionIndex, int attemptNo) {
        Attempt a = new Attempt();
        a.setExamId(exam.getId());
        a.setStudentId(student.getId());
        a.setAttemptNo(attemptNo);
        a.setStatus(AttemptStatus.SUBMITTED);
        a.setStartedAt(Instant.now().minusSeconds(600));
        a.setEndsAt(Instant.now().plusSeconds(600));
        a.setSubmittedAt(Instant.now());
        a = attempts.save(a);
        AttemptQuestion aq = new AttemptQuestion();
        aq.setAttemptId(a.getId());
        aq.setPosition(1);
        aq.setSourceQuestionId(q.getId());
        aq.setType(q.getType());
        aq.setText(q.getText());
        aq.setMarks(new BigDecimal("2"));
        aq.setNegativeMarks(BigDecimal.ZERO);
        aq.setCoId(q.getCoId());
        List<SnapshotOption> opts = new ArrayList<>();
        for (QuestionOption o : q.getOptions()) {
            SnapshotOption so = new SnapshotOption();
            so.setKey(String.valueOf(o.getId()));
            so.setText(o.getText());
            so.setCorrect(o.isCorrect());
            opts.add(so);
        }
        aq.setOptions(opts);
        aq = attemptQuestions.save(aq);
        AttemptAnswer ans = new AttemptAnswer();
        ans.setAttemptId(a.getId());
        ans.setAttemptQuestionId(aq.getId());
        ans.setAnswer(String.valueOf(q.getOptions().get(Integer.parseInt(chosenKeyOfOptionIndex)).getId()));
        ans.setAnsweredAt(Instant.now());
        answers.save(ans);
        return a;
    }

    @Test
    void liveCorrectionRescoresSubmittedAttemptsWithoutStoppingTheExam() {
        Question q = questionService.save(teacherAuth, mcq("Which is a prime?", "2", "4", "6"));
        ExamQuestion eq = new ExamQuestion();
        eq.setExamId(exam.getId());
        eq.setQuestionId(q.getId());
        eq.setMarks(new BigDecimal("2"));
        eq.setPosition(1);
        examQuestions.save(eq);
        Attempt a = submittedAttempt(q, "1", 1); // student chose "4" - wrong
        live.recalculate(superAuth, exam.getId(), "initial scoring");
        assertEquals(0, new BigDecimal("0.00").compareTo(grades.findByAttemptId(a.getId()).orElseThrow().getTotalScore()));

        // 1. accept a second option as correct
        Long wrongId = q.getOptions().get(1).getId();
        assertEquals(1, live.acceptOptions(superAuth, exam.getId(), q.getId(), List.of(wrongId), "Both are acceptable"));
        assertEquals(0, new BigDecimal("2.00").compareTo(grades.findByAttemptId(a.getId()).orElseThrow().getTotalScore()));

        // 2. drop the question: nothing counts
        live.setVoid(superAuth, exam.getId(), q.getId(), "DROPPED", "Question withdrawn");
        Grade g = grades.findByAttemptId(a.getId()).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(g.getMaxScore()));
        assertEquals("DROPPED", examQuestions.findById(eq.getId()).orElseThrow().getVoidMode());

        // 3. everyone gets the marks
        live.setVoid(superAuth, exam.getId(), q.getId(), "FREE_MARKS", "Give marks to all");
        g = grades.findByAttemptId(a.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("2.00").compareTo(g.getTotalScore()));
        assertEquals(0, new BigDecimal("2.00").compareTo(g.getMaxScore()));

        // restore
        live.setVoid(superAuth, exam.getId(), q.getId(), "NONE", "Undo it");
        assertEquals("NONE", examQuestions.findById(eq.getId()).orElseThrow().getVoidMode());
        assertThrows(AppException.class, () -> live.setVoid(teacherAuth, exam.getId(), q.getId(), "DROPPED", "teachers cannot"));
        assertThrows(AppException.class, () -> live.setVoid(superAuth, exam.getId(), q.getId(), "DROPPED", ""), "reason required");
        long audited = jdbc.sql("select count(*) from audit_logs where action like 'OVERRIDE_%' and entity_id = :i").param("i", String.valueOf(exam.getId())).query(Long.class).single();
        assertTrue(audited >= 5);
    }

    @Test
    void coPoAttainmentIsComputedFromStudentResults() {
        Long co1 = jdbc.sql("insert into course_outcomes (subject_id, code, target_percent) values (:s, 'CO1', 60) returning id").param("s", subject.getId()).query(Long.class).single();
        Long po1 = jdbc.sql("insert into program_outcomes (branch_id, code, description) values (null, :c, 'Engineering knowledge') returning id").param("c", "T" + UUID.randomUUID().toString().substring(0, 6)).query(Long.class).single();
        jdbc.sql("insert into co_po_map (co_id, po_id, level) values (:c, :p, 3)").param("c", co1).param("p", po1).update();
        QuestionForm f = mcq("Which is a prime?", "2", "4", "6");
        f.setCoId(co1);
        Question q = questionService.save(teacherAuth, f);
        assertEquals(co1, q.getCoId());
        Attempt a = submittedAttempt(q, "0", 1); // correct
        live.recalculate(superAuth, exam.getId(), "score it");
        OutcomeReportService.Report r = outcomeReports.build(superAuth, new OutcomeReportService.Scope(subject.getId(), null, null));
        assertEquals(1, r.cos().size());
        OutcomeReportService.CoRow row = r.cos().get(0);
        assertEquals(1, row.students());
        assertEquals(1, row.attained());
        assertEquals(3, row.level(), "100% of students attained the CO");
        assertEquals(1, r.pos().size());
        assertEquals(0, new BigDecimal("3.00").compareTo(r.pos().get(0).level()));
        assertEquals(1, r.students().size());
        assertEquals(0, new BigDecimal("100.0").compareTo(r.students().get(0).coPercent().get("CO1")));
        byte[] xlsx = outcomeReports.excel(r);
        byte[] pdf = outcomeReports.pdf(r, null);
        assertEquals('P', xlsx[0]);
        assertEquals("%PDF", new String(pdf, 0, 4));
        assertEquals(a.getExamId(), exam.getId());
    }

    @Test
    void attainmentLevelsFollowTheThresholds() {
        assertEquals(3, OutcomeReportService.levelFor(new BigDecimal("70")));
        assertEquals(2, OutcomeReportService.levelFor(new BigDecimal("69.9")));
        assertEquals(2, OutcomeReportService.levelFor(new BigDecimal("60")));
        assertEquals(1, OutcomeReportService.levelFor(new BigDecimal("50")));
        assertEquals(0, OutcomeReportService.levelFor(new BigDecimal("49.9")));
    }

    @Test
    void scheduledResultsOpenOnlyAtThePublishTime() {
        Instant at = Instant.parse("2026-06-01T10:00:00Z");
        var row = new com.mindbridgex.assessment.dto.StudentAttemptRow(1L, 1L, "E", 1, AttemptStatus.GRADED, at.minusSeconds(900), at.minusSeconds(600), new BigDecimal("1"), new BigDecimal("2"),
                new BigDecimal("50"), true, 0, ResultPolicy.SCHEDULED, at.minusSeconds(300), false, true, at);
        assertFalse(row.isResultVisible(at.minusSeconds(1)));
        assertTrue(row.isResultVisible(at));
        assertTrue(row.isResultVisible(at.plusSeconds(3600)));
    }

    @Test
    void courseOutcomeIsRequiredOnceTheSubjectHasThem() {
        questionService.save(teacherAuth, mcq("No CO defined yet", "a", "b"));
        jdbc.sql("insert into course_outcomes (subject_id, code, target_percent) values (:s, 'CO1', 60)").param("s", subject.getId()).update();
        AppException e = assertThrows(AppException.class, () -> questionService.save(teacherAuth, mcq("Needs a CO now", "a", "b")));
        assertTrue(e.getMessage().contains("Course Outcome"));
    }

    @Test
    void hodAndIqacHaveTheirOwnAccess() throws Exception {
        User hod = user("hod" + UUID.randomUUID().toString().substring(0, 6), RoleType.HOD);
        User iqac = user("iq" + UUID.randomUUID().toString().substring(0, 6), RoleType.IQAC);
        lookup.evictAll();
        mvc.perform(get("/hod").with(as(hod))).andExpect(status().isOk());
        mvc.perform(get("/teacher").with(as(hod))).andExpect(status().isOk());
        mvc.perform(get("/hod").with(as(teacher))).andExpect(status().isForbidden());
        mvc.perform(get("/reports/outcomes").with(as(iqac))).andExpect(status().isOk());
        mvc.perform(get("/teacher/questions").with(as(iqac))).andExpect(status().isForbidden());
        mvc.perform(get("/super").with(as(iqac))).andExpect(status().isForbidden());
    }

    @Test
    void hodApprovesExamsOfTheirBranchOnly() {
        Long branch = jdbc.sql("insert into branches (name, code) values ('B', :c) returning id").param("c", "B" + UUID.randomUUID().toString().substring(0, 6)).query(Long.class).single();
        Long other = jdbc.sql("insert into branches (name, code) values ('O', :c) returning id").param("c", "O" + UUID.randomUUID().toString().substring(0, 6)).query(Long.class).single();
        User hod = user("hodb" + UUID.randomUUID().toString().substring(0, 6), RoleType.HOD);
        jdbc.sql("update users set branch_id = :b where id = :u").param("b", branch).param("u", hod.getId()).update();
        AuthUser hodAuth = new AuthUser(hod.getId(), hod.getUsername(), "Hod", EnumSet.of(RoleType.HOD), false);
        jdbc.sql("update exams set approval_status = 'PENDING' where id = :e").param("e", exam.getId()).update();
        assertThrows(AppException.class, () -> overrides.hodDecision(hodAuth, exam.getId(), true, "ok"), "subject is in no branch yet");
        jdbc.sql("update subjects set branch_id = :b where id = :s").param("b", other).param("s", subject.getId()).update();
        assertThrows(AppException.class, () -> overrides.hodDecision(hodAuth, exam.getId(), true, "ok"), "other branch");
        jdbc.sql("update subjects set branch_id = :b where id = :s").param("b", branch).param("s", subject.getId()).update();
        overrides.hodDecision(hodAuth, exam.getId(), true, "Looks good");
        assertEquals("APPROVED", exams.findById(exam.getId()).orElseThrow().getApprovalStatus());
        assertThrows(AppException.class, () -> overrides.hodDecision(hodAuth, exam.getId(), false, ""), "a reason is needed to reject");
    }

    @Test
    void superAdminCanReleaseResultsEarlyAndItIsLogged() {
        overrides.setResultRelease(superAuth, exam.getId(), Instant.now().plusSeconds(86400 * 15), "Moderation takes 15 days");
        Exam e = exams.findById(exam.getId()).orElseThrow();
        assertEquals(ResultPolicy.SCHEDULED, e.getResultPolicy());
        overrides.setResultRelease(superAuth, exam.getId(), Instant.now(), "Moderation finished early");
        assertFalse(exams.findById(exam.getId()).orElseThrow().getResultsPublishAt().isAfter(Instant.now().plusSeconds(2)));
        assertThrows(AppException.class, () -> overrides.setResultRelease(teacherAuth, exam.getId(), Instant.now(), "no no no"));
        long n = jdbc.sql("select count(*) from audit_logs where action = 'OVERRIDE_RESULT_RELEASE' and entity_id = :i").param("i", String.valueOf(exam.getId())).query(Long.class).single();
        assertEquals(2, n);
    }

    @Test
    void bloomAndUnitAreStoredAndShownInTheBloomReport() {
        QuestionForm f = mcq("Apply Ohm's law", "V=IR", "V=I/R");
        f.setBloom(Bloom.APPLY);
        f.setUnit("Unit 2");
        Question q = questionService.save(teacherAuth, f);
        assertEquals(Bloom.APPLY, questions.findById(q.getId()).orElseThrow().getBloom());
        submittedAttempt(q, "0", 1);
        live.recalculate(superAuth, exam.getId(), "score it");
        OutcomeReportService.Report r = outcomeReports.build(superAuth, new OutcomeReportService.Scope(subject.getId(), null, null));
        assertEquals(1, r.bloom().size());
        assertEquals("Apply", r.bloom().get(0).level());
        assertEquals(0, new BigDecimal("100.0").compareTo(r.bloom().get(0).averagePercent()));
    }
}
