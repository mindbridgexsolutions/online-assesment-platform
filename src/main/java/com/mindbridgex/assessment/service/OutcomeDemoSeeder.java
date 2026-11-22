package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.domain.Bloom;
import com.mindbridgex.assessment.domain.Difficulty;
import com.mindbridgex.assessment.domain.ExamStatus;
import com.mindbridgex.assessment.domain.ExamType;
import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.domain.ResultPolicy;
import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.entity.Attempt;
import com.mindbridgex.assessment.entity.Batch;
import com.mindbridgex.assessment.entity.Exam;
import com.mindbridgex.assessment.entity.ExamAssignment;
import com.mindbridgex.assessment.entity.ExamQuestion;
import com.mindbridgex.assessment.entity.Question;
import com.mindbridgex.assessment.entity.QuestionCategory;
import com.mindbridgex.assessment.entity.QuestionOption;
import com.mindbridgex.assessment.entity.Subject;
import com.mindbridgex.assessment.entity.User;
import com.mindbridgex.assessment.repository.BatchRepository;
import com.mindbridgex.assessment.repository.ExamAssignmentRepository;
import com.mindbridgex.assessment.repository.ExamQuestionRepository;
import com.mindbridgex.assessment.repository.ExamRepository;
import com.mindbridgex.assessment.repository.QuestionCategoryRepository;
import com.mindbridgex.assessment.repository.QuestionRepository;
import com.mindbridgex.assessment.repository.SubjectRepository;
import com.mindbridgex.assessment.repository.UserRepository;
import com.mindbridgex.assessment.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Demo content for the CO-PO screens (only with SEED_DEMO=true): a Data Structures course with three Course Outcomes
 * mapped to Programme Outcomes, 24 tagged questions (Bloom's level, unit, CO), two exams and 20 students who have
 * already taken them with different results, so every attainment report shows real numbers straight away.
 */
@Component
@Order(3)
public class OutcomeDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OutcomeDemoSeeder.class);
    private static final String PASSWORD = "Demo@12345";

    // text, right answer, three wrong answers
    private static final String[][] QS = {
            // CO1: recall / understand (units 1-2)
            {"Which data structure works on the LIFO principle?", "Stack", "Queue", "Array", "Tree"},
            {"Which data structure works on the FIFO principle?", "Queue", "Stack", "Heap", "Graph"},
            {"What is the time complexity of accessing an array element by index?", "O(1)", "O(n)", "O(log n)", "O(n^2)"},
            {"Which of these is a linear data structure?", "Linked list", "Binary tree", "Graph", "Heap"},
            {"In a singly linked list each node stores data and", "a pointer to the next node", "a pointer to the previous node", "the array size", "the parent node"},
            {"Which operation adds an element to a stack?", "Push", "Pop", "Peek", "Dequeue"},
            {"A queue in which insertion and deletion happen at both ends is called", "Deque", "Circular queue", "Priority queue", "Stack"},
            {"The index of the first element in a C or Java array is", "0", "1", "-1", "Depends on the size"},
            // CO2: apply (units 2-3)
            {"What is the result of evaluating the postfix expression 2 3 4 * + ?", "14", "20", "9", "24"},
            {"After push(5), push(8), pop(), push(3), the top of the stack is", "3", "5", "8", "Empty"},
            {"Inserting at the head of a singly linked list takes", "O(1)", "O(n)", "O(log n)", "O(n log n)"},
            {"The in-order traversal of a binary search tree gives the keys in", "Sorted order", "Reverse order", "Level order", "Random order"},
            {"Which structure is best for checking balanced parentheses?", "Stack", "Queue", "Heap", "Hash table"},
            {"A circular queue of size 5 holds how many elements at most (one slot kept empty)?", "4", "5", "6", "3"},
            {"Searching for a key in a sorted array of 1024 items using binary search needs at most about", "10 comparisons", "512 comparisons", "1024 comparisons", "32 comparisons"},
            {"Converting infix A+B*C to postfix gives", "ABC*+", "AB+C*", "ABC+*", "A+BC*"},
            // CO3: analyse / evaluate (units 3-4)
            {"Which sorting algorithm has the best worst-case time complexity?", "Merge sort", "Quick sort", "Bubble sort", "Insertion sort"},
            {"Why is a hash table lookup O(1) on average but O(n) in the worst case?", "Many keys can collide into one bucket", "The table must be sorted first", "Keys are stored in a linked list only", "Hash functions are slow"},
            {"For a graph with V vertices and E edges stored as an adjacency matrix, the space needed is", "O(V^2)", "O(V + E)", "O(E)", "O(V log V)"},
            {"A balanced BST is preferred over a plain BST because it", "Keeps height near log n", "Uses less memory per node", "Stores duplicates", "Avoids recursion"},
            {"Which traversal uses a queue?", "Breadth-first search", "Depth-first search", "In-order traversal", "Pre-order traversal"},
            {"Which is the best structure to implement a priority scheduler?", "Heap", "Stack", "Array without order", "Singly linked list"},
            {"Dijkstra's algorithm fails when the graph has", "Negative edge weights", "Cycles", "Many vertices", "Undirected edges"},
            {"The recurrence T(n) = 2T(n/2) + n solves to", "O(n log n)", "O(n)", "O(n^2)", "O(log n)"}
    };
    private static final Bloom[] BLOOMS = {Bloom.REMEMBER, Bloom.REMEMBER, Bloom.REMEMBER, Bloom.REMEMBER, Bloom.UNDERSTAND, Bloom.REMEMBER, Bloom.UNDERSTAND, Bloom.REMEMBER,
            Bloom.APPLY, Bloom.APPLY, Bloom.APPLY, Bloom.UNDERSTAND, Bloom.APPLY, Bloom.APPLY, Bloom.APPLY, Bloom.APPLY,
            Bloom.ANALYZE, Bloom.ANALYZE, Bloom.ANALYZE, Bloom.EVALUATE, Bloom.ANALYZE, Bloom.EVALUATE, Bloom.ANALYZE, Bloom.ANALYZE};

    private final AppProperties props;
    private final JdbcClient jdbc;
    private final PlatformTransactionManager txm;
    private final PasswordEncoder encoder;
    private final UserRepository users;
    private final SubjectRepository subjects;
    private final BatchRepository batches;
    private final QuestionCategoryRepository categories;
    private final QuestionRepository questions;
    private final ExamRepository exams;
    private final ExamQuestionRepository examQuestions;
    private final ExamAssignmentRepository assignments;
    private final AttemptService attemptService;

    public OutcomeDemoSeeder(AppProperties props, JdbcClient jdbc, PlatformTransactionManager txm, PasswordEncoder encoder, UserRepository users,
                             SubjectRepository subjects, BatchRepository batches, QuestionCategoryRepository categories, QuestionRepository questions,
                             ExamRepository exams, ExamQuestionRepository examQuestions, ExamAssignmentRepository assignments, AttemptService attemptService) {
        this.props = props;
        this.jdbc = jdbc;
        this.txm = txm;
        this.encoder = encoder;
        this.users = users;
        this.subjects = subjects;
        this.batches = batches;
        this.categories = categories;
        this.questions = questions;
        this.exams = exams;
        this.examQuestions = examQuestions;
        this.assignments = assignments;
        this.attemptService = attemptService;
    }

    private record Setup(List<Long> examIds, List<User> students) {
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.getSeed().isDemoData() || subjects.existsByCodeIgnoreCase("DS201")) {
            return;
        }
        try {
            Setup setup = new TransactionTemplate(txm).execute(s -> create());
            takeExams(setup);
            log.warn("CO-PO demo data created: course DS201 (3 COs, 24 questions, 2 exams) answered by 20 students (ds01..ds20, password '{}').", PASSWORD);
        } catch (RuntimeException e) {
            log.error("Could not create the CO-PO demo data (the rest of the app is unaffected): {}", e.toString(), e);
        }
    }

    private Setup create() {
        Long comp = jdbc.sql("select id from branches where code = 'COMP'").query(Long.class).optional().orElse(null);
        User teacher = users.findByUsernameIgnoreCase("teacher1").orElseGet(() -> user("teacher1", "Tara Teacher", RoleType.TEACHER));
        Subject ds = new Subject();
        ds.setName("Data Structures");
        ds.setCode("DS201");
        ds.getTeacherIds().add(teacher.getId());
        ds = subjects.save(ds);
        if (comp != null) {
            jdbc.sql("update subjects set branch_id = :b where id = :s").param("b", comp).param("s", ds.getId()).update();
        }
        String[][] cos = {{"CO1", "Explain the basic data structures and their operations"}, {"CO2", "Apply stacks, queues, lists and trees to solve problems"},
                {"CO3", "Analyse and compare algorithms and choose a suitable data structure"}};
        long[] coIds = new long[3];
        for (int i = 0; i < 3; i++) {
            coIds[i] = jdbc.sql("insert into course_outcomes (subject_id, code, description, target_percent) values (:s, :c, :d, 60) returning id")
                    .param("s", ds.getId()).param("c", cos[i][0]).param("d", cos[i][1]).query(Long.class).single();
        }
        // CO -> PO levels (PO1..PO4 created by the outcomes demo)
        int[][] map = {{3, 2, 0, 0}, {2, 3, 2, 0}, {1, 3, 3, 2}};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 4; j++) {
                if (map[i][j] > 0) {
                    jdbc.sql("insert into co_po_map (co_id, po_id, level) select :c, id, :l from program_outcomes where code = :p")
                            .param("c", coIds[i]).param("l", map[i][j]).param("p", "PO" + (j + 1)).update();
                }
            }
        }
        QuestionCategory cat = new QuestionCategory();
        cat.setSubject(ds);
        cat.setName("Unit tests");
        cat = categories.save(cat);
        List<Question> qs = new ArrayList<>();
        for (int i = 0; i < QS.length; i++) {
            Question q = new Question();
            q.setSubject(ds);
            q.setCategory(cat);
            q.setType(QuestionType.MCQ_SINGLE);
            q.setDifficulty(i % 8 < 3 ? Difficulty.EASY : i % 8 < 6 ? Difficulty.MEDIUM : Difficulty.HARD);
            q.setText(QS[i][0]);
            q.setDefaultMarks(new BigDecimal(i >= 16 ? "2" : "1"));
            q.setBloom(BLOOMS[i]);
            q.setUnit("Unit " + (i / 6 + 1));
            q.setCoId(coIds[i / 8]);
            for (int k = 1; k < 5; k++) {
                QuestionOption o = new QuestionOption();
                o.setQuestion(q);
                o.setPosition(k - 1);
                o.setText(QS[i][k]);
                o.setCorrect(k == 1);
                q.getOptions().add(o);
            }
            qs.add(questions.save(q));
        }
        Set<Long> studentIds = new HashSet<>();
        List<User> students = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            User st = user(String.format("ds%02d", i), "DS Student " + i, RoleType.STUDENT);
            if (st.getRollNo() == null || st.getRollNo().isBlank()) {
                st.setRollNo(String.format("DS24%03d", i));
                st = users.save(st);
            }
            students.add(st);
            studentIds.add(st.getId());
        }
        Batch batch = new Batch();
        batch.setName("Data Structures - Division A");
        batch.setCode("DS-A");
        batch.setStudentIds(studentIds);
        batch = batches.save(batch);
        Exam e1 = exam(ds, "DS Unit Test 1", ExamType.UNIT_TEST, 25, "6", qs.subList(0, 12), batch);
        Exam e2 = exam(ds, "DS Mid-Semester Exam", ExamType.MID_SEM, 60, "15", qs, batch);
        return new Setup(List.of(e1.getId(), e2.getId()), students);
    }

    private Exam exam(Subject ds, String title, ExamType type, int minutes, String pass, List<Question> qs, Batch batch) {
        Exam e = new Exam();
        e.setSubject(ds);
        e.setTitle(title);
        e.setInstructions("Choose one answer per question. Wrong answers carry negative marks.");
        e.setDurationMinutes(minutes);
        e.setStartAt(Instant.now().minus(2, ChronoUnit.HOURS));
        e.setEndAt(Instant.now().plus(60, ChronoUnit.DAYS));
        e.setMaxAttempts(1);
        e.setPassingMarks(new BigDecimal(pass));
        e.setNegativeMarking(true);
        e.setNegativeFraction(new BigDecimal("0.25"));
        e.setResultPolicy(ResultPolicy.IMMEDIATE);
        e.setStatus(ExamStatus.PUBLISHED);
        e.setExamType(type);
        e.setApprovalStatus("APPROVED");
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
        ExamAssignment a = new ExamAssignment();
        a.setExamId(e.getId());
        a.setBatchId(batch.getId());
        assignments.save(a);
        return e;
    }

    /** Every student answers both exams; a per-student ability and a per-CO difficulty shape the results. */
    private void takeExams(Setup setup) {
        Random rnd = new Random(2026);
        double[] coBoost = {0.10, 0.0, -0.10};
        for (int s = 0; s < setup.students().size(); s++) {
            User st = setup.students().get(s);
            double ability = 0.52 + 0.45 * (s / 19.0);
            AuthUser au = new AuthUser(st.getId(), st.getUsername(), st.getFullName(), EnumSet.of(RoleType.STUDENT), false);
            for (Long examId : setup.examIds()) {
                // two students skip the mid-semester exam, so the reports also cope with missing attempts
                if (s >= 18 && examId.equals(setup.examIds().get(1))) {
                    continue;
                }
                Attempt a = attemptService.startOrResume(au, examId, "127.0.0.1", "demo-seeder");
                AttemptService.PlayerView view = attemptService.openPlayer(au, a.getId());
                List<AttemptService.AnswerInput> inputs = new ArrayList<>();
                for (AttemptService.PlayerQuestion pq : view.questions()) {
                    int idx = indexOf(pq.text());
                    if (idx < 0 || rnd.nextDouble() < 0.06) {
                        continue; // left unanswered
                    }
                    double p = Math.min(0.97, Math.max(0.08, ability + coBoost[idx / 8]));
                    String wanted = rnd.nextDouble() < p ? QS[idx][1] : QS[idx][1 + 1 + rnd.nextInt(3)];
                    String key = pq.options().stream().filter(o -> o.text().equals(wanted)).map(AttemptService.PlayerOption::key).findFirst().orElse(null);
                    if (key != null) {
                        inputs.add(new AttemptService.AnswerInput(pq.id(), key, false));
                    }
                }
                attemptService.submit(au, a.getId(), view.playerSession(), inputs);
            }
        }
    }

    private static int indexOf(String text) {
        for (int i = 0; i < QS.length; i++) {
            if (QS[i][0].equals(text)) {
                return i;
            }
        }
        return -1;
    }

    private User user(String username, String name, RoleType role) {
        return users.findByUsernameIgnoreCase(username).orElseGet(() -> {
            User u = new User();
            u.setUsername(username);
            u.setFullName(name);
            u.setEmail(username + "@example.com");
            u.setPasswordHash(encoder.encode(PASSWORD));
            u.setRoles(EnumSet.of(role));
            return users.save(u);
        });
    }
}
