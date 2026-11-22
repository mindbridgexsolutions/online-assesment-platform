package com.mindbridgex.assessment.papers;

import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.entity.Question;
import com.mindbridgex.assessment.entity.Subject;
import com.mindbridgex.assessment.entity.User;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.QuestionRepository;
import com.mindbridgex.assessment.repository.SubjectRepository;
import com.mindbridgex.assessment.repository.UserRepository;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.security.UserLookupService;
import com.mindbridgex.assessment.service.DocxQuestionParser;
import com.mindbridgex.assessment.service.ExamUploadService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Uploaded files are kept as named papers; merging into the bank or into another paper is always explicit; Word round trip. Needs the PostgreSQL test database. */
@SpringBootTest
@ActiveProfiles("test")
class QuestionPaperTest {

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
    @Autowired PasswordEncoder encoder;
    @Autowired UserLookupService lookup;
    @Autowired ExamUploadService papers;
    @Autowired DocxQuestionParser wordParser;
    @Autowired com.mindbridgex.assessment.service.FileStorageService storage;
    @Autowired com.mindbridgex.assessment.repository.ExamRepository examRepo;
    @Autowired com.mindbridgex.assessment.service.ExamService examService;
    @Autowired com.mindbridgex.assessment.service.QuestionService questionService;

    User teacher;
    User otherTeacher;
    AuthUser teacherAuth;
    AuthUser otherAuth;
    Subject subject;
    Subject otherSubject;

    @BeforeEach
    void data() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        teacher = user("tea" + tag);
        otherTeacher = user("oth" + tag);
        subject = subject("Subj " + tag, "S" + tag, teacher);
        otherSubject = subject("Other " + tag, "O" + tag, teacher);
        teacherAuth = new AuthUser(teacher.getId(), teacher.getUsername(), "Teacher", EnumSet.of(RoleType.TEACHER), false);
        otherAuth = new AuthUser(otherTeacher.getId(), otherTeacher.getUsername(), "Other", EnumSet.of(RoleType.TEACHER), false);
        lookup.evictAll();
    }

    private User user(String name) {
        User u = new User();
        u.setUsername(name);
        u.setFullName(name);
        u.setEmail(name + "@example.com");
        u.setPasswordHash(encoder.encode("Test@12345"));
        u.setRoles(EnumSet.of(RoleType.TEACHER));
        return users.save(u);
    }

    private Subject subject(String name, String code, User t) {
        Subject s = new Subject();
        s.setName(name);
        s.setCode(code);
        s.getTeacherIds().add(t.getId());
        return subjects.save(s);
    }

    private long bankSize() {
        return questions.findAll().stream().filter(q -> q.getSubject().getId().equals(subject.getId())).count();
    }

    private MockMultipartFile csv(String name, String... questionRows) {
        StringBuilder sb = new StringBuilder("subject_code,type,question,options,correct,marks\n");
        for (String r : questionRows) {
            sb.append(r.replace("{S}", subject.getCode())).append('\n');
        }
        return new MockMultipartFile("file", name, "text/csv", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile mcqCsv(String name) {
        return csv(name,
                "{S},MCQ_SINGLE,Capital of India?,Mumbai|New Delhi|Kolkata|Chennai,2,2",
                "{S},MCQ_MULTI,Which are prime?,2|4|5|9,\"1,3\",3",
                "{S},MCQ_SINGLE,Largest planet?,Earth|Jupiter|Mars,2,1",
                "{S},ESSAY,Describe gravity,,,5",
                "WRONG,MCQ_SINGLE,Belongs elsewhere,a|b,1,1");
    }

    // ------------------------------------------------------------------ a file is one named paper; the bank is untouched until the merge
    @Test
    void uploadedFileIsAPaperAndNothingEntersTheBankUntilMerged() {
        Long sid = papers.uploadPaper(teacherAuth, subject.getId(), mcqCsv("unit1.csv"));
        ExamUploadService.Session s = papers.view(teacherAuth, sid);
        assertEquals("unit1.csv", s.fileName());
        assertEquals("CSV", s.source());
        assertEquals(subject.getId(), s.subjectId());
        assertNull(s.examId(), "a paper does not need an exam");
        assertEquals(5, s.uploaded());
        assertEquals(3, s.valid());
        assertEquals(2, s.failed(), "the essay row and the other-subject row cannot be held in this paper");
        assertTrue(s.rows().stream().anyMatch(r -> r.message() != null && r.message().contains("multiple-choice")));
        assertEquals("B", s.rows().get(0).item().correct(), "option numbers become letters");
        assertEquals("A,C", s.rows().get(1).item().correct());
        assertEquals("Multiple answers", s.rows().get(1).type());
        assertEquals(0, bankSize(), "uploading must not touch the Question Bank");

        List<ExamUploadService.Paper> list = papers.papers(teacherAuth);
        ExamUploadService.Paper p = list.stream().filter(x -> x.id().equals(sid)).findFirst().orElseThrow();
        assertEquals("PENDING", p.status());
        assertEquals(5, p.questions());
        assertEquals(3, p.valid());

        ExamUploadService.ConfirmResult r = papers.mergeToBank(teacherAuth, sid);
        assertEquals(3, r.created());
        assertEquals(2, r.skipped());
        assertEquals(3, bankSize());
        assertTrue(questions.findAll().stream().filter(q -> q.getSubject().getId().equals(subject.getId())).allMatch(q -> q.getSource().startsWith("bulk upload: unit1.csv")));
        assertEquals("CONFIRMED", papers.view(teacherAuth, sid).status());
        assertThrows(AppException.class, () -> papers.mergeToBank(teacherAuth, sid), "a paper is merged only once");
        assertDoesNotThrow(() -> papers.recheck(teacherAuth, sid), "a merged paper stays open as a saved paper that can be changed and sent again");
    }

    @Test
    void exactDuplicatesAgainstTheBankAreFlaggedWhenAPaperIsUploaded() {
        papers.mergeToBank(teacherAuth, papers.uploadPaper(teacherAuth, subject.getId(), mcqCsv("first.csv")));
        assertEquals(3, bankSize());
        ExamUploadService.Session again = papers.view(teacherAuth, papers.uploadPaper(teacherAuth, subject.getId(), mcqCsv("again.csv")));
        assertEquals(3, again.duplicates(), "the same three MCQs are already in the bank");
        assertEquals(0, again.valid());
    }

    @Test
    void aPaperWithNothingValidCannotBeMerged() {
        Long sid = papers.uploadPaper(teacherAuth, subject.getId(), csv("bad.csv", "{S},ESSAY,Not an MCQ,,,5"));
        assertEquals(0, papers.view(teacherAuth, sid).valid());
        assertThrows(AppException.class, () -> papers.mergeToBank(teacherAuth, sid));
        assertEquals("PENDING", papers.view(teacherAuth, sid).status(), "it stays a draft so it can be fixed");
        assertEquals(0, bankSize());
    }

    @Test
    void otherTeachersCannotSeeOrUsePapers() {
        Long sid = papers.uploadPaper(teacherAuth, subject.getId(), mcqCsv("mine.csv"));
        assertTrue(papers.papers(otherAuth).isEmpty());
        assertThrows(AppException.class, () -> papers.view(otherAuth, sid));
        assertThrows(AppException.class, () -> papers.mergeToBank(otherAuth, sid));
        assertThrows(AppException.class, () -> papers.uploadPaper(otherAuth, subject.getId(), mcqCsv("x.csv")), "not their subject");
    }

    // ------------------------------------------------------------------ merging papers
    @Test
    void twoPapersMergeIntoOneNewPaperWithDuplicatesFlaggedAndOriginalsKept() {
        Long a = papers.uploadPaper(teacherAuth, subject.getId(), csv("a.csv",
                "{S},MCQ_SINGLE,Capital of India?,Mumbai|New Delhi|Kolkata|Chennai,2,1",
                "{S},MCQ_SINGLE,Largest planet?,Earth|Jupiter|Mars,2,1"));
        Long b = papers.uploadPaper(teacherAuth, subject.getId(), csv("b.csv",
                "{S},MCQ_SINGLE,Largest planet?,Earth|Jupiter|Mars,2,1",
                "{S},MCQ_SINGLE,Fastest animal?,Cheetah|Snail,1,1"));
        Long m = papers.mergePapers(teacherAuth, List.of(a, b), "Unit 1 combined");
        ExamUploadService.Session s = papers.view(teacherAuth, m);
        assertEquals("Unit 1 combined", s.fileName());
        assertEquals("MERGE", s.source());
        assertEquals(4, s.uploaded());
        assertEquals(3, s.valid());
        assertEquals(1, s.duplicates(), "Largest planet is in both papers");
        assertEquals(List.of(1, 2, 3, 4), s.rows().stream().map(ExamUploadService.Row::rowNo).toList());
        assertEquals("PENDING", papers.view(teacherAuth, a).status(), "originals stay as they are");
        assertEquals("PENDING", papers.view(teacherAuth, b).status());
        assertEquals(0, bankSize());

        ExamUploadService.Row first = s.rows().get(0);
        papers.edit(teacherAuth, m, first.id(), new ExamUploadService.Edit("Capital city of India?", List.of("Mumbai", "New Delhi", "Kolkata", "Chennai"), "B", "2", "", "", "", "", null, null, false, null));
        assertEquals("Capital city of India?", papers.view(teacherAuth, m).rows().get(0).item().text(), "a merged paper can be edited before it goes to the bank");

        assertEquals(3, papers.mergeToBank(teacherAuth, m).created(), "the flagged duplicate is skipped unless the teacher keeps it");
        assertEquals(3, bankSize());
    }

    @Test
    void mergeRulesAreChecked() {
        Long a = papers.uploadPaper(teacherAuth, subject.getId(), csv("a.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,1"));
        MockMultipartFile other = new MockMultipartFile("file", "o.csv", "text/csv",
                ("subject_code,type,question,options,correct,marks\n" + otherSubject.getCode() + ",MCQ_SINGLE,Q two?,x|y,1,1\n").getBytes(StandardCharsets.UTF_8));
        Long o = papers.uploadPaper(teacherAuth, otherSubject.getId(), other);
        assertThrows(AppException.class, () -> papers.mergePapers(teacherAuth, List.of(a), "x"), "needs two papers");
        assertThrows(AppException.class, () -> papers.mergePapers(teacherAuth, List.of(a, a), "x"), "the same paper twice is one paper");
        assertThrows(AppException.class, () -> papers.mergePapers(teacherAuth, List.of(a, o), "x"), "different subjects");
        assertThrows(AppException.class, () -> papers.mergePapers(otherAuth, List.of(a, o), "x"), "not their papers");
    }

    // ------------------------------------------------------------------ edit in Word and come back
    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 30; y++) {
                img.setRGB(x, y, (x * 6) << 8 | y);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private byte[] pictureDocx() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String[] head = {"Q No", "Question", "Option A", "Option B", "Correct", "Marks"};
            XWPFTable t = doc.createTable(3, head.length);
            for (int i = 0; i < head.length; i++) {
                t.getRow(0).getCell(i).setText(head[i]);
            }
            t.getRow(1).getCell(0).setText("1");
            t.getRow(1).getCell(1).setText("Plain question?");
            t.getRow(1).getCell(2).setText("Yes");
            t.getRow(1).getCell(3).setText("No");
            t.getRow(1).getCell(4).setText("A");
            t.getRow(2).getCell(0).setText("2");
            XWPFRun q = t.getRow(2).getCell(1).getParagraphs().get(0).createRun();
            q.addPicture(new ByteArrayInputStream(png()), org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG, "q.png", org.apache.poi.util.Units.toEMU(40), org.apache.poi.util.Units.toEMU(30));
            t.getRow(2).getCell(2).setText("Circuit A");
            t.getRow(2).getCell(3).setText("Circuit B");
            t.getRow(2).getCell(4).setText("B");
            t.getRow(2).getCell(5).setText("2");
            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void paperDownloadsAsWordWithPicturesAndAnEditedWordFileReplacesIt() throws Exception {
        Long sid = papers.uploadPaper(teacherAuth, subject.getId(), new MockMultipartFile("file", "pics.docx", "application/octet-stream", pictureDocx()));
        ExamUploadService.Session before = papers.view(teacherAuth, sid);
        assertEquals(2, before.uploaded());
        assertNotNull(before.rows().get(1).item().image(), "picture read from the uploaded Word file");

        byte[] word = papers.exportDocx(teacherAuth, sid);
        List<DocxQuestionParser.ParsedRow> parsed = wordParser.parse(new ByteArrayInputStream(word));
        assertEquals(2, parsed.size(), "the downloaded Word file holds every row");
        assertEquals("A", parsed.get(0).correct());
        assertEquals("Plain question?", parsed.get(0).question().text());
        assertEquals("Circuit B", parsed.get(1).options().get(1).text());

        // edit in Word: change the text of question 1 and the correct answer of question 2, then upload it back
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFTable t = doc.getTables().get(0);
            var q1 = t.getRow(1).getCell(1);
            while (q1.getParagraphs().get(0).getRuns().size() > 0) {
                q1.getParagraphs().get(0).removeRun(0);
            }
            q1.getParagraphs().get(0).createRun().setText("Edited in Word?");
            var correct2 = t.getRow(2).getCell(t.getRow(0).getTableCells().size() - 6);
            while (correct2.getParagraphs().get(0).getRuns().size() > 0) {
                correct2.getParagraphs().get(0).removeRun(0);
            }
            correct2.getParagraphs().get(0).createRun().setText("A");
            doc.write(out);
            papers.replaceFromDocx(teacherAuth, sid, new MockMultipartFile("file", "edited.docx", "application/octet-stream", out.toByteArray()));
        }
        ExamUploadService.Session after = papers.view(teacherAuth, sid);
        assertEquals(2, after.uploaded());
        assertEquals("Edited in Word?", after.rows().get(0).item().text());
        assertEquals("A", after.rows().get(1).item().correct());
        assertNotNull(after.rows().get(1).item().image(), "the picture survives the Word round trip");
        assertEquals("pics.docx", after.fileName(), "it is still the same paper");
        assertEquals(0, bankSize());
    }

    @Test
    void replaceFromWordRejectsAnEmptyOrNonWordFileAndLeavesThePaperAlone() {
        Long sid = papers.uploadPaper(teacherAuth, subject.getId(), mcqCsv("keep.csv"));
        assertThrows(AppException.class, () -> papers.replaceFromDocx(teacherAuth, sid, new MockMultipartFile("file", "x.csv", "text/csv", "a".getBytes())));
        assertThrows(AppException.class, () -> papers.replaceFromDocx(teacherAuth, sid, new MockMultipartFile("file", "x.docx", "application/octet-stream", "not a zip".getBytes())));
        assertEquals(5, papers.view(teacherAuth, sid).uploaded());
    }

    // ------------------------------------------------------------------ validation dashboard
    @Test
    void metricsAddUpMissingAnswersAreNeverGuessedAndExcludedQuestionsAreNotMerged() {
        Long sid = papers.uploadPaper(teacherAuth, subject.getId(), csv("dash.csv",
                "{S},MCQ_SINGLE,Capital of India?,Mumbai|New Delhi,2,1",
                "{S},MCQ_SINGLE,No answer given?,Yes|No,,1",
                "{S},MCQ_SINGLE,Largest planet?,Earth|Jupiter,2,1",
                "{S},ESSAY,Not an MCQ,,,5"));
        ExamUploadService.Session s = papers.view(teacherAuth, sid);
        ExamUploadService.Metrics m = s.metrics();
        assertEquals(4, m.total());
        assertEquals(m.total(), m.valid() + m.warnings() + m.needsReview() + m.invalid(), "the four validation statuses add up to the total");
        assertTrue(m.needsReview() + m.invalid() >= 2, "the question without an answer and the essay are not valid");
        assertEquals(2, m.eligible());
        ExamUploadService.Row noAnswer = s.rows().get(1);
        assertEquals("ERROR", noAnswer.status(), "no correct answer is ever guessed");
        assertFalse(noAnswer.eligible());

        ExamUploadService.Row planet = s.rows().get(2);
        papers.setExcluded(teacherAuth, sid, List.of(planet.id()), true);
        ExamUploadService.Session after = papers.view(teacherAuth, sid);
        assertEquals(1, after.metrics().eligible());
        assertTrue(after.rows().get(2).excluded());
        assertThrows(AppException.class, () -> papers.setExcluded(teacherAuth, sid, List.of(), true), "nothing ticked");
        assertThrows(AppException.class, () -> papers.setExcluded(otherAuth, sid, List.of(planet.id()), false), "someone else's paper");

        papers.setExcluded(teacherAuth, sid, List.of(planet.id()), false);
        assertEquals(2, papers.view(teacherAuth, sid).metrics().eligible(), "include again");
        papers.setExcluded(teacherAuth, sid, List.of(planet.id()), true);
        assertEquals(1, papers.mergeToBank(teacherAuth, sid).created());
        assertEquals(1, bankSize(), "only the selected, valid question reached the bank");
    }

    @Test
    void allQuestionsLeftOutMeansNothingToMergeAndThePaperStaysADraft() {
        Long sid = papers.uploadPaper(teacherAuth, subject.getId(), csv("none.csv", "{S},MCQ_SINGLE,Only one?,a|b,1,1"));
        papers.setExcluded(teacherAuth, sid, List.of(papers.view(teacherAuth, sid).rows().get(0).id()), true);
        assertThrows(AppException.class, () -> papers.mergeToBank(teacherAuth, sid));
        assertEquals("PENDING", papers.view(teacherAuth, sid).status());
        assertEquals(0, bankSize());
    }

    @Test
    void aPictureCanBeMovedBetweenQuestionAndOptionAndTheOtherWayRound() throws Exception {
        Long sid = papers.uploadPaper(teacherAuth, subject.getId(), new MockMultipartFile("file", "pics.docx", "application/octet-stream", pictureDocx()));
        ExamUploadService.Row row = papers.view(teacherAuth, sid).rows().get(1);
        String picture = row.item().image();
        assertNotNull(picture);
        papers.moveImage(teacherAuth, sid, row.id(), "Q", "B");
        ExamUploadService.Item moved = papers.view(teacherAuth, sid).rows().get(1).item();
        assertNull(moved.image());
        assertEquals(picture, moved.options().get(1).image());
        papers.moveImage(teacherAuth, sid, row.id(), "B", "Q");
        assertEquals(picture, papers.view(teacherAuth, sid).rows().get(1).item().image());
        assertThrows(AppException.class, () -> papers.moveImage(teacherAuth, sid, row.id(), "A", "B"), "no picture in A");
        assertThrows(AppException.class, () -> papers.moveImage(teacherAuth, sid, row.id(), "Q", "Q"));
        assertThrows(AppException.class, () -> papers.moveImage(teacherAuth, sid, row.id(), "Q", "Z"));
        assertThrows(AppException.class, () -> papers.moveImage(otherAuth, sid, row.id(), "Q", "A"));
    }

    @Test
    void discardingAPaperDeletesItsPicturesOnlyWhenNothingElseUsesThem() throws Exception {
        Long sid = papers.uploadPaper(teacherAuth, subject.getId(), new MockMultipartFile("file", "pics.docx", "application/octet-stream", pictureDocx()));
        String picture = papers.view(teacherAuth, sid).rows().get(1).item().image();
        assertTrue(storage.load(picture).exists());
        papers.discard(teacherAuth, sid);
        assertThrows(AppException.class, () -> storage.load(picture), "an unused picture is removed with its discarded draft");

        Long kept = papers.uploadPaper(teacherAuth, subject.getId(), new MockMultipartFile("file", "pics2.docx", "application/octet-stream", pictureDocx()));
        papers.mergeToBank(teacherAuth, kept);
        String used = questions.findAll().stream().filter(q -> q.getSubject().getId().equals(subject.getId()) && q.getImagePath() != null)
                .map(q -> q.getImagePath()).findFirst().orElseThrow();
        Long copy = papers.uploadPaper(teacherAuth, subject.getId(), new MockMultipartFile("file", "pics3.docx", "application/octet-stream", pictureDocx()));
        papers.discard(teacherAuth, copy);
        assertTrue(storage.load(used).exists(), "a picture used by a bank question is never deleted");
    }

    @Test
    void aCorruptPictureInsideWordIsRejectedWithoutBreakingTheUpload() throws Exception {
        byte[] docx;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String[] head = {"Q No", "Question", "Option A", "Option B", "Correct", "Marks"};
            XWPFTable t = doc.createTable(2, head.length);
            for (int i = 0; i < head.length; i++) {
                t.getRow(0).getCell(i).setText(head[i]);
            }
            t.getRow(1).getCell(0).setText("1");
            t.getRow(1).getCell(1).getParagraphs().get(0).createRun().addPicture(new ByteArrayInputStream("this is not a png at all".getBytes()),
                    org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG, "bad.png", org.apache.poi.util.Units.toEMU(40), org.apache.poi.util.Units.toEMU(30));
            t.getRow(1).getCell(2).setText("x");
            t.getRow(1).getCell(3).setText("y");
            t.getRow(1).getCell(4).setText("A");
            t.getRow(1).getCell(5).setText("1");
            doc.write(out);
            docx = out.toByteArray();
        }
        Long sid = papers.uploadPaper(teacherAuth, subject.getId(), new MockMultipartFile("file", "bad.docx", "application/octet-stream", docx));
        ExamUploadService.Row r = papers.view(teacherAuth, sid).rows().get(0);
        assertNull(r.item().image(), "a file that is not really a picture is never stored");
        assertFalse(r.eligible(), "a question that has no text and no usable picture cannot be merged");
    }

    // ------------------------------------------------------------------ duplicates are re-checked at merge time; identical papers are recognised
    @Test
    void twoDraftsWithTheSameQuestionsCannotBothPutThemIntoTheBank() {
        Long a = papers.uploadPaper(teacherAuth, subject.getId(), csv("a.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,1", "{S},MCQ_SINGLE,Q two?,x|y,2,1"));
        Long b = papers.uploadPaper(teacherAuth, subject.getId(), csv("b.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,1", "{S},MCQ_SINGLE,Q three?,x|y,1,1"));
        assertEquals(2, papers.view(teacherAuth, b).valid(), "both look new while nothing is merged yet");
        papers.mergeToBank(teacherAuth, a);
        assertEquals(2, bankSize());
        ExamUploadService.ConfirmResult r = papers.mergeToBank(teacherAuth, b);
        assertEquals(1, r.created(), "Q one is already in the bank, so only Q three is added");
        assertEquals(3, bankSize(), "no question is in the bank twice");
    }

    @Test
    void anIdenticalPaperIsRecognisedButAnEditedOneIsNot() {
        Long a = papers.uploadPaper(teacherAuth, subject.getId(), csv("a.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,1"));
        assertNull(papers.sameAs(teacherAuth, a));
        Long same = papers.uploadPaper(teacherAuth, subject.getId(), csv("copy.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,1"));
        assertEquals("a.csv", papers.sameAs(teacherAuth, same));
        Long changed = papers.uploadPaper(teacherAuth, subject.getId(), csv("c.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,1", "{S},MCQ_SINGLE,Q two?,x|y,1,1"));
        assertNull(papers.sameAs(teacherAuth, changed));
    }

    // ------------------------------------------------------------------ publish a paper to students
    private User student(String name) {
        User u = new User();
        u.setUsername(name);
        u.setFullName(name);
        u.setEmail(name + "@example.com");
        u.setPasswordHash(encoder.encode("Test@12345"));
        u.setRoles(EnumSet.of(RoleType.STUDENT));
        return users.save(u);
    }

    private ExamUploadService.PublishForm form(String students) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return new ExamUploadService.PublishForm("Unit test", null, 30, now.plusHours(1), now.plusDays(1), null, null, students);
    }

    @Test
    void aPaperCanBePublishedToStudentsAndExistingBankQuestionsAreReused() {
        User stu = student("stu" + UUID.randomUUID().toString().substring(0, 8));
        lookup.evictAll();
        Long a = papers.uploadPaper(teacherAuth, subject.getId(), csv("a.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,2", "{S},MCQ_SINGLE,Q two?,x|y,2,3"));
        ExamUploadService.PublishResult r = papers.publishPaper(teacherAuth, a, form(stu.getUsername()));
        assertTrue(r.published(), r.message());
        assertEquals(2, r.questions());
        assertEquals(2, r.newInBank());
        assertEquals(2, bankSize());
        assertEquals("CONFIRMED", papers.view(teacherAuth, a).status());
        assertEquals(com.mindbridgex.assessment.domain.ExamStatus.PUBLISHED, examRepo.findById(r.examId()).orElseThrow().getStatus());
        assertEquals(2, examService.detail(teacherAuth, r.examId()).totalQuestions());

        // the same questions again, from another paper: reused, nothing copied
        Long b = papers.uploadPaper(teacherAuth, subject.getId(), csv("b.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,2", "{S},MCQ_SINGLE,Q three?,x|y,1,1"));
        ExamUploadService.PublishResult r2 = papers.publishPaper(teacherAuth, b, form(stu.getUsername()));
        assertEquals(2, r2.questions());
        assertEquals(1, r2.newInBank());
        assertEquals(3, bankSize());
    }

    @Test
    void publishingNeedsAudienceTitleAndAValidPaper() {
        Long a = papers.uploadPaper(teacherAuth, subject.getId(), csv("a.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,1"));
        assertThrows(AppException.class, () -> papers.publishPaper(teacherAuth, a, form("")), "nobody would see it");
        assertThrows(AppException.class, () -> papers.publishPaper(otherAuth, a, form("someone")), "not their paper");
        Long bad = papers.uploadPaper(teacherAuth, subject.getId(), csv("bad.csv", "{S},ESSAY,Not an MCQ,,,5"));
        assertThrows(AppException.class, () -> papers.publishPaper(teacherAuth, bad, form("someone")), "nothing valid to publish");
        assertEquals(0, bankSize(), "a refused publish saves nothing");
    }

    @Test
    void publishingAPaperNeverWaitsForApprovalEvenWhenTheInstitutionRequiresItForOtherExams() {
        Object target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(examService);
        org.springframework.test.util.ReflectionTestUtils.setField(target, "approvalRequired", true);
        try {
            User stu = student("stu" + UUID.randomUUID().toString().substring(0, 8));
            lookup.evictAll();
            Long a = papers.uploadPaper(teacherAuth, subject.getId(), csv("a.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,2"));
            ExamUploadService.PublishResult r = papers.publishPaper(teacherAuth, a, form(stu.getUsername()));
            assertTrue(r.published(), r.message());
            assertEquals(com.mindbridgex.assessment.domain.ExamStatus.PUBLISHED, examRepo.findById(r.examId()).orElseThrow().getStatus());
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(target, "approvalRequired", false);
        }
    }

    // ------------------------------------------------------------------ a saved paper can be changed and sent again
    @Test
    void aSavedPaperCanLoseAndGainQuestionsBeSentAgainAndDeletedWithoutTouchingTheBank() {
        User stu = student("stu" + UUID.randomUUID().toString().substring(0, 8));
        lookup.evictAll();
        Long a = papers.uploadPaper(teacherAuth, subject.getId(), csv("a.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,1", "{S},MCQ_SINGLE,Q two?,x|y,2,1", "{S},MCQ_SINGLE,Q three?,x|y,1,1"));
        papers.mergeToBank(teacherAuth, a);
        assertEquals(3, bankSize());
        assertEquals("CONFIRMED", papers.view(teacherAuth, a).status());

        // still editable: remove one question, and the rows are not wrongly flagged as duplicates of themselves
        ExamUploadService.Session s = papers.view(teacherAuth, a);
        papers.deleteItem(teacherAuth, a, s.rows().get(2).id());
        papers.recheck(teacherAuth, a);
        ExamUploadService.Session after = papers.view(teacherAuth, a);
        assertEquals(2, after.uploaded());
        assertEquals(0, after.duplicates());
        assertEquals(2, after.metrics().eligible());

        // add a question from the bank (Q three is in the bank but no longer in the paper)
        Long qThree = questions.findAll().stream().filter(q -> q.getSubject().getId().equals(subject.getId()) && q.getText().equals("Q three?")).findFirst().orElseThrow().getId();
        assertEquals(1, papers.addFromBank(teacherAuth, a, List.of(qThree)));
        ExamUploadService.Session grown = papers.view(teacherAuth, a);
        assertEquals(3, grown.uploaded());
        assertEquals("Q three?", grown.rows().get(2).item().text());
        assertEquals("A", grown.rows().get(2).item().correct());

        // send it: the questions are reused from the bank, nothing is copied
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        ExamUploadService.PublishResult r = papers.publishPaper(teacherAuth, a, new ExamUploadService.PublishForm("Again", null, 20, now.plusHours(1), now.plusDays(1), null, null, stu.getUsername()));
        assertTrue(r.published(), r.message());
        assertEquals(3, r.questions());
        assertEquals(0, r.newInBank());
        assertEquals(3, bankSize());

        // delete the saved paper: gone from the list, the bank and the exam stay
        papers.discard(teacherAuth, a);
        assertTrue(papers.papers(teacherAuth).stream().noneMatch(p -> p.id().equals(a)));
        assertEquals(3, bankSize());
        assertEquals(3, examService.detail(teacherAuth, r.examId()).totalQuestions());
        assertThrows(AppException.class, () -> papers.addFromBank(teacherAuth, a, List.of(qThree)), "a deleted paper cannot be changed");
    }

    @Test
    void addingFromTheBankIsLimitedToMultipleChoiceQuestionsOfTheSameSubject() {
        Long a = papers.uploadPaper(teacherAuth, subject.getId(), csv("a.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,1"));
        papers.mergeToBank(teacherAuth, a);
        Long mine = questions.findAll().stream().filter(q -> q.getSubject().getId().equals(subject.getId())).findFirst().orElseThrow().getId();
        Long other = papers.uploadPaper(teacherAuth, otherSubject.getId(), new MockMultipartFile("file", "o.csv", "text/csv",
                ("subject_code,type,question,options,correct,marks\n" + otherSubject.getCode() + ",MCQ_SINGLE,Elsewhere?,x|y,1,1\n").getBytes(StandardCharsets.UTF_8)));
        assertThrows(AppException.class, () -> papers.addFromBank(teacherAuth, other, List.of(mine)), "another subject's question is refused");
        assertThrows(AppException.class, () -> papers.addFromBank(teacherAuth, other, List.of()), "nothing ticked");
        assertThrows(AppException.class, () -> papers.addFromBank(otherAuth, a, List.of(mine)), "not their paper");
    }

    // ------------------------------------------------------------------ deleting a question; pointing at the duplicate
    @Test
    void aQuestionCanBeDeletedUnlessAnExamUsesIt() {
        User stu = student("stu" + UUID.randomUUID().toString().substring(0, 8));
        lookup.evictAll();
        Long used = papers.uploadPaper(teacherAuth, subject.getId(), csv("used.csv", "{S},MCQ_SINGLE,In an exam?,x|y,1,1"));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        papers.publishPaper(teacherAuth, used, new ExamUploadService.PublishForm("Exam", null, 20, now.plusHours(1), now.plusDays(1), null, null, stu.getUsername()));
        Long free = papers.uploadPaper(teacherAuth, subject.getId(), csv("free.csv", "{S},MCQ_SINGLE,Not in any exam?,x|y,1,1"));
        papers.mergeToBank(teacherAuth, free);
        assertEquals(2, bankSize());
        Long usedId = questions.findAll().stream().filter(q -> q.getText().equals("In an exam?")).findFirst().orElseThrow().getId();
        Long freeId = questions.findAll().stream().filter(q -> q.getText().equals("Not in any exam?")).findFirst().orElseThrow().getId();

        assertThrows(AppException.class, () -> questionService.delete(teacherAuth, usedId), "an exam uses it: archive instead");
        assertThrows(AppException.class, () -> questionService.delete(otherAuth, freeId), "not their subject");
        questionService.delete(teacherAuth, freeId);
        assertEquals(1, bankSize());
        assertTrue(questions.findById(freeId).isEmpty());
        assertTrue(questions.findById(usedId).isPresent());
    }

    @Test
    void anIdenticalPaperIsPointedOutByItsId() {
        Long a = papers.uploadPaper(teacherAuth, subject.getId(), csv("a.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,1"));
        Long copy = papers.uploadPaper(teacherAuth, subject.getId(), csv("copy.csv", "{S},MCQ_SINGLE,Q one?,x|y,1,1"));
        ExamUploadService.SamePaper same = papers.sameAsPaper(teacherAuth, copy);
        assertEquals(a, same.id());
        assertEquals("a.csv", same.name());
    }
}
