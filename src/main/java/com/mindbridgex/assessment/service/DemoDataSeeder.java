package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.domain.*;
import com.mindbridgex.assessment.entity.*;
import com.mindbridgex.assessment.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Optional demo content (app.seed.demo-data=true, never on by default): one teacher, five students, a batch, a subject,
 * 16 questions covering every question type and a published exam that is open right now.
 */
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String DEMO_PASSWORD = "Demo@12345";

    private final AppProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SubjectRepository subjects;
    private final BatchRepository batches;
    private final QuestionCategoryRepository categories;
    private final QuestionRepository questions;
    private final ExamRepository exams;
    private final ExamQuestionRepository examQuestions;
    private final ExamQuestionRuleRepository rules;
    private final ExamAssignmentRepository assignments;

    public DemoDataSeeder(AppProperties props, UserRepository users, PasswordEncoder encoder, SubjectRepository subjects,
                          BatchRepository batches, QuestionCategoryRepository categories, QuestionRepository questions,
                          ExamRepository exams, ExamQuestionRepository examQuestions, ExamQuestionRuleRepository rules,
                          ExamAssignmentRepository assignments) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
        this.subjects = subjects;
        this.batches = batches;
        this.categories = categories;
        this.questions = questions;
        this.exams = exams;
        this.examQuestions = examQuestions;
        this.rules = rules;
        this.assignments = assignments;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.getSeed().isDemoData()) {
            return;
        }
        if (!subjects.existsByCodeIgnoreCase("GK101")) {
            seedGeneralKnowledge();
        }
        if (!subjects.existsByCodeIgnoreCase("MATH101")) {
            seedMcqDemo();
        }
        seedOutcomes();
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.simple.JdbcClient jdbc;

    /** Branches, Programme Outcomes, Course Outcomes, the CO-PO map and CO tags on the demo questions (only when none exist). */
    private void seedOutcomes() {
        if (jdbc.sql("select count(*) from branches").query(Long.class).single() > 0) {
            return;
        }
        Long comp = jdbc.sql("insert into branches (name, code) values ('Computer Engineering', 'COMP') returning id").query(Long.class).single();
        jdbc.sql("insert into branches (name, code) values ('Mechanical Engineering', 'MECH')").update();
        User hod = user("hod1", "Hari HoD", "hod1@example.com", null, RoleType.HOD);
        User iqac = user("iqac1", "Indu IQAC", "iqac1@example.com", null, RoleType.IQAC);
        jdbc.sql("update users set branch_id = :b where id in (:ids)").param("b", comp).param("ids", List.of(hod.getId(), iqac.getId())).update();
        String[][] pos = {{"PO1", "Engineering knowledge"}, {"PO2", "Problem analysis"}, {"PO3", "Design and development of solutions"}, {"PO4", "Conduct investigations of complex problems"}};
        List<Long> poIds = new ArrayList<>();
        for (String[] p : pos) {
            poIds.add(jdbc.sql("insert into program_outcomes (branch_id, code, description) values (null, :c, :d) returning id").param("c", p[0]).param("d", p[1]).query(Long.class).single());
        }
        for (String code : List.of("GK101", "MATH101", "SCI101")) {
            List<Long> sid = jdbc.sql("select id from subjects where code = :c").param("c", code).query(Long.class).list();
            if (sid.isEmpty()) {
                continue;
            }
            Long s = sid.get(0);
            jdbc.sql("update subjects set branch_id = :b where id = :s").param("b", comp).param("s", s).update();
            Long co1 = jdbc.sql("insert into course_outcomes (subject_id, code, description, target_percent) values (:s, 'CO1', 'Recall and understand the core facts and concepts', 60) returning id").param("s", s).query(Long.class).single();
            Long co2 = jdbc.sql("insert into course_outcomes (subject_id, code, description, target_percent) values (:s, 'CO2', 'Apply the concepts to solve problems', 60) returning id").param("s", s).query(Long.class).single();
            int[][] map = {{0, 3}, {1, 2}, {2, 0}};
            long[] cos = {co1, co2};
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 4; j++) {
                    int lv = (i == 0 && j == 0) ? 3 : (i == 0 && j == 1) ? 2 : (i == 1 && j == 1) ? 3 : (i == 1 && j == 2) ? 2 : (i == 1 && j == 3) ? 1 : 0;
                    if (lv > 0) {
                        jdbc.sql("insert into co_po_map (co_id, po_id, level) values (:c, :p, :l)").param("c", cos[i]).param("p", poIds.get(j)).param("l", lv).update();
                    }
                }
            }
            jdbc.sql("update questions set co_id = case when id % 2 = 0 then :a else :b end where subject_id = :s and co_id is null").param("a", co1).param("b", co2).param("s", s).update();
        }
        log.info("Demo outcomes created: 2 branches, 4 Programme Outcomes, CO1/CO2 with CO-PO mapping on the demo subjects.");
    }

    private void seedGeneralKnowledge() {
        User teacher = user("teacher1", "Tara Teacher", "teacher1@example.com", null, RoleType.TEACHER);
        Set<Long> studentIds = new HashSet<>();
        for (int i = 1; i <= 5; i++) {
            studentIds.add(user("student" + i, "Student " + i, "student" + i + "@example.com", "2024-00" + i, RoleType.STUDENT).getId());
        }
        Batch batch = new Batch();
        batch.setName("Demo batch A");
        batch.setCode("BATCH-A");
        batch.setStudentIds(studentIds);
        batch = batches.save(batch);

        Subject subject = new Subject();
        subject.setName("General Knowledge");
        subject.setCode("GK101");
        subject.getTeacherIds().add(teacher.getId());
        subject = subjects.save(subject);

        QuestionCategory science = category(subject, "Science");
        QuestionCategory geography = category(subject, "Geography");
        QuestionCategory maths = category(subject, "Maths");

        single(subject, science, Difficulty.EASY, "Which planet is known as the Red Planet?", "Mars", "Venus", "Jupiter", "Saturn");
        single(subject, science, Difficulty.EASY, "What gas do plants absorb from the air?", "Carbon dioxide", "Oxygen", "Nitrogen", "Helium");
        single(subject, geography, Difficulty.MEDIUM, "Which is the longest river in India?", "Ganga", "Yamuna", "Godavari", "Narmada");
        multi(subject, maths, Difficulty.MEDIUM, "Select all prime numbers.", true, new String[]{"2", "5", "7"}, new String[]{"4", "9"});
        multi(subject, science, Difficulty.HARD, "Which of these are noble gases?", false, new String[]{"Neon", "Argon"}, new String[]{"Oxygen", "Chlorine"});
        trueFalse(subject, science, Difficulty.EASY, "The Sun is a star.", true);
        trueFalse(subject, geography, Difficulty.EASY, "Mount Everest is in South America.", false);
        text(subject, geography, QuestionType.FILL_BLANK, Difficulty.EASY, "The capital of France is ____.", "Paris");
        text(subject, science, QuestionType.FILL_BLANK, Difficulty.MEDIUM, "Water boils at ____ degrees Celsius at sea level.", "100");
        text(subject, science, QuestionType.SHORT_ANSWER, Difficulty.MEDIUM, "Name any three primary colours.", "red", "blue", "yellow");
        matching(subject, geography, Difficulty.MEDIUM, "Match each country with its capital.",
                new String[][]{{"India", "New Delhi"}, {"Japan", "Tokyo"}, {"Italy", "Rome"}});
        numeric(subject, maths, Difficulty.MEDIUM, "What is the value of pi to two decimal places?", "3.14", "0.01");
        numeric(subject, maths, Difficulty.EASY, "What is 12 x 12?", "144", "0");
        essay(subject, science, Difficulty.HARD, "Explain the water cycle in your own words.");
        single(subject, maths, Difficulty.HARD, "What is the derivative of x squared?", "2x", "x", "x squared", "2");
        trueFalse(subject, maths, Difficulty.MEDIUM, "Zero is an even number.", true);

        Exam exam = new Exam();
        exam.setSubject(subject);
        exam.setTitle("General Knowledge - Demo Test");
        exam.setInstructions("This demo covers every question type. Answer all questions; there is no time pressure.");
        exam.setDurationMinutes(30);
        exam.setStartAt(Instant.now().minus(Duration.ofHours(1)));
        exam.setEndAt(Instant.now().plus(Duration.ofDays(30)));
        exam.setMaxAttempts(2);
        exam.setPassingMarks(new BigDecimal("12"));
        exam.setNegativeMarking(true);
        exam.setNegativeFraction(new BigDecimal("0.25"));
        exam.setResultPolicy(ResultPolicy.IMMEDIATE);
        exam.setStatus(ExamStatus.PUBLISHED);
        exam = exams.save(exam);

        ExamQuestionRule ruleEasy = new ExamQuestionRule();
        ruleEasy.setExamId(exam.getId());
        ruleEasy.setDifficulty(Difficulty.EASY);
        ruleEasy.setQuestionCount(3);
        ruleEasy.setMarksPerQuestion(BigDecimal.ONE);
        rules.save(ruleEasy);
        int pos = 1;
        for (Question q : questions.findAll()) {
            if (q.getDifficulty() != Difficulty.EASY) {
                ExamQuestion eq = new ExamQuestion();
                eq.setExamId(exam.getId());
                eq.setQuestionId(q.getId());
                eq.setMarks(q.getDefaultMarks());
                eq.setPosition(pos++);
                examQuestions.save(eq);
            }
        }
        ExamAssignment a = new ExamAssignment();
        a.setExamId(exam.getId());
        a.setBatchId(batch.getId());
        assignments.save(a);
        log.warn("Demo data created. Sign in as teacher1 / student1..student5 with password '{}'.", DEMO_PASSWORD);
    }

    /** More faculty, 30 students in three batches, two MCQ-only subjects and four exams with negative marking. */
    private void seedMcqDemo() {
        User t2 = user("teacher2", "Rahul Verma", "teacher2@example.com", null, RoleType.TEACHER);
        User t3 = user("teacher3", "Priya Nair", "teacher3@example.com", null, RoleType.TEACHER);

        Set<Long> batchA = new HashSet<>();
        Set<Long> batchB = new HashSet<>();
        Set<Long> batchC = new HashSet<>();
        for (int i = 6; i <= 30; i++) {
            User st = user("student" + i, "Student " + i, "student" + i + "@example.com", String.format("2024-%03d", i), RoleType.STUDENT);
            (i <= 15 ? batchB : batchC).add(st.getId());
        }
        Batch b = batch("Demo batch B", "BATCH-B", batchB);
        Batch c = batch("Demo batch C", "BATCH-C", batchC);
        // BATCH-A (student1..5) is created by the general-knowledge demo; look it up by assigning through its students
        Batch a = batches.findAll().stream().filter(x -> "BATCH-A".equalsIgnoreCase(x.getCode())).findFirst().orElse(null);

        Subject math = new Subject();
        math.setName("Mathematics");
        math.setCode("MATH101");
        math.getTeacherIds().add(t2.getId());
        math = subjects.save(math);
        Subject sci = new Subject();
        sci.setName("Science");
        sci.setCode("SCI101");
        sci.getTeacherIds().add(t3.getId());
        sci = subjects.save(sci);

        QuestionCategory alg = category(math, "Arithmetic & Algebra");
        java.util.List<Question> mq = new java.util.ArrayList<>();
        String[][] m = {
                {"What is 15 + 27?", "42", "32", "52", "41"},
                {"What is 9 x 8?", "72", "63", "81", "64"},
                {"What is the square root of 144?", "12", "14", "11", "13"},
                {"What is 100 divided by 4?", "25", "20", "40", "50"},
                {"Solve: 3x = 21. What is x?", "7", "6", "8", "9"},
                {"What is 20% of 250?", "50", "25", "40", "60"},
                {"What is the next prime number after 7?", "11", "9", "13", "8"},
                {"What is 2 to the power 5?", "32", "16", "64", "25"},
                {"What is the perimeter of a square with side 6?", "24", "36", "12", "30"},
                {"What is the area of a rectangle 8 by 5?", "40", "26", "13", "45"},
                {"Simplify: 3/4 + 1/4", "1", "3/8", "4/8", "2"},
                {"What is 7 x 13?", "91", "81", "93", "84"},
                {"How many degrees are in a right angle?", "90", "45", "180", "60"},
                {"What is 1000 - 357?", "643", "743", "653", "633"},
                {"What is the value of 5 squared minus 3 squared?", "16", "4", "34", "8"}};
        for (String[] r : m) {
            mq.add(mcq(math, alg, Difficulty.EASY, r[0], r[1], r[2], r[3], r[4]));
        }
        QuestionCategory phys = category(sci, "General Science");
        java.util.List<Question> sq = new java.util.ArrayList<>();
        String[][] sc = {
                {"What is the chemical symbol for water?", "H2O", "HO2", "O2H", "H2"},
                {"Which planet is closest to the Sun?", "Mercury", "Venus", "Earth", "Mars"},
                {"What gas do humans need to breathe in?", "Oxygen", "Nitrogen", "Helium", "Hydrogen"},
                {"Which organ pumps blood around the body?", "Heart", "Liver", "Lungs", "Kidney"},
                {"What is the boiling point of water at sea level?", "100 C", "90 C", "80 C", "120 C"},
                {"Which force pulls objects towards the Earth?", "Gravity", "Friction", "Magnetism", "Tension"},
                {"Plants make food by a process called?", "Photosynthesis", "Respiration", "Digestion", "Evaporation"},
                {"Which is the hardest natural substance?", "Diamond", "Iron", "Gold", "Quartz"},
                {"What is the unit of electric current?", "Ampere", "Volt", "Ohm", "Watt"},
                {"Sound cannot travel through?", "Vacuum", "Water", "Air", "Steel"},
                {"How many bones are in an adult human body?", "206", "186", "250", "300"},
                {"Which gas is most abundant in Earth's atmosphere?", "Nitrogen", "Oxygen", "Carbon dioxide", "Argon"},
                {"What is the speed of light closest to?", "300,000 km/s", "30,000 km/s", "3,000 km/s", "3 million km/s"},
                {"Which vitamin do we get from sunlight?", "Vitamin D", "Vitamin C", "Vitamin A", "Vitamin B12"},
                {"What is the center of an atom called?", "Nucleus", "Electron", "Proton shell", "Orbit"}};
        for (String[] r : sc) {
            sq.add(mcq(sci, phys, Difficulty.EASY, r[0], r[1], r[2], r[3], r[4]));
        }

        Instant now = Instant.now();
        Exam e1 = exam(math, "Mathematics MCQ Test (negative marking)", "15 multiple-choice questions, 1 mark each. Every wrong answer deducts 0.25 marks; unanswered questions score 0.",
                30, now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(60)), "7.5", "0.25", ResultPolicy.IMMEDIATE, ExamStatus.PUBLISHED, mq);
        Exam e2 = exam(sci, "Science MCQ Quiz (results released by teacher)", "15 multiple-choice questions, 1 mark each. Every wrong answer deducts 0.33 marks. Results are shown after your teacher releases them.",
                20, now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(60)), "7.5", "0.33", ResultPolicy.MANUAL, ExamStatus.PUBLISHED, sq);
        Exam e3 = exam(math, "Mathematics - Upcoming Test", "This exam opens tomorrow so you can see how upcoming exams look to students.",
                30, now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(60)), "7.5", "0.25", ResultPolicy.AFTER_WINDOW, ExamStatus.PUBLISHED, mq);
        exam(sci, "Science - Draft (not yet published)", "Draft exam: assign it and press Publish to try the teacher workflow.",
                20, now.plus(Duration.ofDays(2)), now.plus(Duration.ofDays(60)), "7.5", "0.25", ResultPolicy.IMMEDIATE, ExamStatus.DRAFT, sq);

        assign(e1, a, b);
        assign(e2, b, c);
        assign(e3, c);
        log.warn("MCQ demo data created: teacher2/teacher3 and student6..student30 (password '{}'), batches BATCH-B/BATCH-C, "
                + "subjects MATH101/SCI101 and 4 exams.", DEMO_PASSWORD);
    }

    private Batch batch(String name, String code, Set<Long> studentIds) {
        Batch b = new Batch();
        b.setName(name);
        b.setCode(code);
        b.setStudentIds(studentIds);
        return batches.save(b);
    }

    private Question mcq(Subject s, QuestionCategory c, Difficulty d, String text, String right, String... wrong) {
        Question q = base(s, c, QuestionType.MCQ_SINGLE, d, text, "1");
        opt(q, right, null, true);
        for (String w : wrong) {
            opt(q, w, null, false);
        }
        return questions.save(q);
    }

    private Exam exam(Subject s, String title, String instructions, int minutes, Instant start, Instant end, String pass,
                      String negative, ResultPolicy policy, ExamStatus status, java.util.List<Question> qs) {
        Exam e = new Exam();
        e.setSubject(s);
        e.setTitle(title);
        e.setInstructions(instructions);
        e.setDurationMinutes(minutes);
        e.setStartAt(start);
        e.setEndAt(end);
        e.setMaxAttempts(1);
        e.setPassingMarks(new BigDecimal(pass));
        e.setNegativeMarking(true);
        e.setNegativeFraction(new BigDecimal(negative));
        e.setResultPolicy(policy);
        e.setStatus(status);
        e = exams.save(e);
        int pos = 1;
        for (Question q : qs) {
            ExamQuestion eq = new ExamQuestion();
            eq.setExamId(e.getId());
            eq.setQuestionId(q.getId());
            eq.setMarks(q.getDefaultMarks());
            eq.setPosition(pos++);
            examQuestions.save(eq);
        }
        return e;
    }

    private void assign(Exam e, Batch... bs) {
        for (Batch b : bs) {
            if (b == null) {
                continue;
            }
            ExamAssignment a = new ExamAssignment();
            a.setExamId(e.getId());
            a.setBatchId(b.getId());
            assignments.save(a);
        }
    }

    private User user(String username, String name, String email, String roll, RoleType role) {
        var existing = users.findByUsernameIgnoreCase(username);
        if (existing.isPresent()) {
            return existing.get();
        }
        User u = new User();
        u.setUsername(username);
        u.setFullName(name);
        u.setEmail(email);
        u.setRollNo(roll);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        u.setRoles(EnumSet.of(role));
        return users.save(u);
    }

    private QuestionCategory category(Subject s, String name) {
        QuestionCategory c = new QuestionCategory();
        c.setSubject(s);
        c.setName(name);
        return categories.save(c);
    }

    private Question base(Subject s, QuestionCategory c, QuestionType t, Difficulty d, String text, String marks) {
        Question q = new Question();
        q.setSubject(s);
        q.setCategory(c);
        q.setType(t);
        q.setDifficulty(d);
        q.setText(text);
        q.setDefaultMarks(new BigDecimal(marks));
        return q;
    }

    private void opt(Question q, String text, String match, boolean correct) {
        QuestionOption o = new QuestionOption();
        o.setQuestion(q);
        o.setPosition(q.getOptions().size());
        o.setText(text);
        o.setMatchText(match);
        o.setCorrect(correct);
        q.getOptions().add(o);
    }

    private void single(Subject s, QuestionCategory c, Difficulty d, String text, String right, String... wrong) {
        Question q = base(s, c, QuestionType.MCQ_SINGLE, d, text, "1");
        opt(q, right, null, true);
        for (String w : wrong) {
            opt(q, w, null, false);
        }
        questions.save(q);
    }

    private void multi(Subject s, QuestionCategory c, Difficulty d, String text, boolean partial, String[] right, String[] wrong) {
        Question q = base(s, c, QuestionType.MCQ_MULTI, d, text, "2");
        q.setPartialMarking(partial);
        for (String r : right) {
            opt(q, r, null, true);
        }
        for (String w : wrong) {
            opt(q, w, null, false);
        }
        questions.save(q);
    }

    private void trueFalse(Subject s, QuestionCategory c, Difficulty d, String text, boolean answer) {
        Question q = base(s, c, QuestionType.TRUE_FALSE, d, text, "1");
        opt(q, "True", null, answer);
        opt(q, "False", null, !answer);
        questions.save(q);
    }

    private void text(Subject s, QuestionCategory c, QuestionType t, Difficulty d, String text, String... answers) {
        Question q = base(s, c, t, d, text, t == QuestionType.SHORT_ANSWER ? "3" : "1");
        for (String a : answers) {
            opt(q, a, null, true);
        }
        questions.save(q);
    }

    private void matching(Subject s, QuestionCategory c, Difficulty d, String text, String[][] pairs) {
        Question q = base(s, c, QuestionType.MATCHING, d, text, "3");
        for (String[] p : pairs) {
            opt(q, p[0], p[1], true);
        }
        questions.save(q);
    }

    private void numeric(Subject s, QuestionCategory c, Difficulty d, String text, String answer, String tolerance) {
        Question q = base(s, c, QuestionType.NUMERIC, d, text, "2");
        q.setNumericAnswer(new BigDecimal(answer));
        q.setNumericTolerance(new BigDecimal(tolerance));
        questions.save(q);
    }

    private void essay(Subject s, QuestionCategory c, Difficulty d, String text) {
        questions.save(base(s, c, QuestionType.ESSAY, d, text, "5"));
    }
}
