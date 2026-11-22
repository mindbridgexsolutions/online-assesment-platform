package com.mindbridgex.assessment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindbridgex.assessment.domain.Difficulty;
import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.dto.OptionForm;
import com.mindbridgex.assessment.dto.QuestionForm;
import com.mindbridgex.assessment.entity.Exam;
import com.mindbridgex.assessment.entity.Question;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.QuestionRepository;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.util.ContentHasher;
import com.mindbridgex.assessment.util.TabularReader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;

/**
 * Bulk MCQ upload from the standard DOCX template, with a review step. Parsed rows are staged in
 * upload_sessions / upload_items; nothing reaches the Question Bank or the exam until the teacher confirms.
 */
@Service
public class ExamUploadService {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final DocxQuestionParser parser;
    private final ExamService exams;
    private final QuestionService questionService;
    private final QuestionRepository questions;
    private final QuestionHasher hasher;
    private final CoPoService copo;
    private final FileStorageService storage;
    private final AuditService audit;
    private final AccessService access;
    private final PaperDocxWriter docxWriter;
    private final DocxFreeFormParser freeForm;
    private final com.mindbridgex.assessment.repository.QuestionCategoryRepository categories;
    private final com.mindbridgex.assessment.repository.SubjectRepository subjects;

    public record Opt(String text, String image) {
    }

    /** What is kept for each row; "problem" is set when the cell itself was unreadable. */
    public record Item(String qNo, String text, String image, List<Opt> options, String correct, String marks, String co, String difficulty, String problem, String bloom, String unit,
                       String warning, String explanation, String category, String tags) {
        public Item(String qNo, String text, String image, List<Opt> options, String correct, String marks, String co, String difficulty, String problem, String bloom, String unit) {
            this(qNo, text, image, options, correct, marks, co, difficulty, problem, bloom, unit, null, null, null, null);
        }
    }

    /**
     * One question of a paper. "status" is the internal VALID / DUPLICATE / ERROR; "vstatus" is what the dashboard shows: VALID, WARNING, NEEDS_REVIEW or INVALID
     * (duplicate status is separate and never counted twice); "eligible" is whether the question would be merged right now.
     */
    public record Row(Long id, int rowNo, String status, String message, boolean keep, Item item, List<Boolean> correctFlags, String type,
                      String content, String vstatus, boolean excluded, boolean eligible) {
    }

    /** Dashboard numbers, all counted from the stored rows. The four validation statuses add up to "total"; duplicates are counted on their own. */
    public record Metrics(int total, int valid, int warnings, int needsReview, int invalid, int duplicates, int eligible, int selected, int excluded,
                          int missingAnswers, int missingOptions, int withImages, int imageIssues) {
    }

    public record Session(Long id, Long examId, String fileName, String status, List<Row> rows, int uploaded, int valid, int duplicates, int failed,
                          Long subjectId, String subjectName, String source, Metrics metrics) {
    }

    /** One line of the "Question papers" list. */
    public record Paper(Long id, String fileName, String subjectName, Long examId, String examTitle, String status, String source, java.time.Instant createdAt,
                        int questions, int valid, int duplicates, int failed) {
    }

    public record ConfirmResult(int created, int skipped, int failed) {
    }

    public ExamUploadService(JdbcClient jdbc, ObjectMapper json, DocxQuestionParser parser, ExamService exams, QuestionService questionService, QuestionRepository questions,
                             QuestionHasher hasher, CoPoService copo, FileStorageService storage, AuditService audit, AccessService access, PaperDocxWriter docxWriter, DocxFreeFormParser freeForm,
                             com.mindbridgex.assessment.repository.QuestionCategoryRepository categories, com.mindbridgex.assessment.repository.SubjectRepository subjects) {
        this.jdbc = jdbc;
        this.json = json;
        this.parser = parser;
        this.exams = exams;
        this.questionService = questionService;
        this.questions = questions;
        this.hasher = hasher;
        this.copo = copo;
        this.storage = storage;
        this.audit = audit;
        this.access = access;
        this.docxWriter = docxWriter;
        this.freeForm = freeForm;
        this.categories = categories;
        this.subjects = subjects;
    }

    // ------------------------------------------------------------------ upload
    /** The exam page's "Upload Questions (.docx)": stages the file as a paper that belongs to this exam. */
    @Transactional
    public Long upload(AuthUser user, Long examId, MultipartFile file) {
        Exam exam = exams.requireEditable(user, examId);
        String name = fileName(file);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw AppException.bad("Only Word (.docx) files in the standard template can be uploaded here.");
        }
        String subjectCode = exam.getSubject().getCode();
        return stage(user, exam.getSubject().getId(), examId, name, "DOCX", parseAnyDocx(file, subjectCode));
    }

    /**
     * "Question papers" upload: a .docx (standard paper template, or the Question Bank's Word table), .xlsx or .csv is staged as one named paper.
     * Nothing reaches the Question Bank until the teacher merges the paper.
     */
    @Transactional
    public Long uploadPaper(AuthUser user, Long subjectId, MultipartFile file) {
        access.requireSubject(user, subjectId);
        String name = fileName(file);
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".docx") || lower.endsWith(".xlsx") || lower.endsWith(".csv"))) {
            throw AppException.bad("Upload a Word (.docx), Excel (.xlsx) or CSV (.csv) file.");
        }
        String subjectCode = jdbc.sql("select code from subjects where id = :s").param("s", subjectId).query(String.class).single();
        List<DocxQuestionParser.ParsedRow> parsed;
        String source;
        if (lower.endsWith(".docx")) {
            source = "DOCX";
            parsed = parseAnyDocx(file, subjectCode);
        } else {
            source = lower.endsWith(".xlsx") ? "XLSX" : "CSV";
            parsed = fromTabular(TabularReader.read(file, DocxQuestionParser.MAX_ROWS), subjectCode, null);
        }
        if (parsed.isEmpty()) {
            throw AppException.bad("No question rows were found in the file.");
        }
        return stage(user, subjectId, null, name, source, parsed);
    }

    private static String fileName(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.bad("Choose a file first.");
        }
        return file.getOriginalFilename() == null ? "questions.docx" : file.getOriginalFilename();
    }

    /**
     * A Word file can come in three layouts, tried in this order: the paper template table, the Question Bank's Word table
     * (question | options | correct ...), or plain paragraphs ("Question 1. ...", "A. ...", "Correct Answer: B").
     */
    private List<DocxQuestionParser.ParsedRow> parseAnyDocx(MultipartFile file, String subjectCode) {
        AppException first;
        try {
            return parseDocx(file);
        } catch (AppException e) {
            first = e;
        }
        try {
            return fromTabular(TabularReader.read(file, DocxQuestionParser.MAX_ROWS), subjectCode, first);
        } catch (AppException ignored) {
            // no usable table: try the paragraph layout
        }
        List<DocxQuestionParser.ParsedRow> rows;
        try {
            rows = freeForm.parse(file.getInputStream());
        } catch (IOException e) {
            throw AppException.bad("The file could not be read.");
        }
        if (rows.isEmpty()) {
            throw AppException.bad("No questions were found. Use the Word paper template, the Question Bank Word table, or write each question as "
                    + "\"Question 1. ...\", options \"A. ...\" to \"D. ...\" and a line \"Correct Answer: B\".");
        }
        return rows;
    }

    private List<DocxQuestionParser.ParsedRow> parseDocx(MultipartFile file) {
        try {
            return parser.parse(file.getInputStream());
        } catch (IOException e) {
            throw AppException.bad("The file could not be read.");
        }
    }

    private Long stage(AuthUser user, Long subjectId, Long examId, String name, String source, List<DocxQuestionParser.ParsedRow> parsed) {
        Long sid = jdbc.sql("insert into upload_sessions (exam_id, subject_id, file_name, source, created_by) values (:e, :sub, :f, :src, :u) returning id")
                .param("e", examId).param("sub", subjectId).param("f", name.length() > 250 ? name.substring(0, 250) : name).param("src", source).param("u", user.getId())
                .query(Long.class).single();
        insertParsed(sid, parsed);
        reevaluate(sid, subjectId);
        audit.log("UPLOAD_PARSED", examId == null ? "Subject" : "Exam", examId == null ? subjectId : examId, name + ", " + parsed.size() + " row(s)");
        return sid;
    }

    private void insertParsed(Long sid, List<DocxQuestionParser.ParsedRow> parsed) {
        for (DocxQuestionParser.ParsedRow p : parsed) {
            List<Opt> opts = new ArrayList<>();
            StringBuilder problem = new StringBuilder();
            StringBuilder warning = new StringBuilder();
            addProblem(problem, p.question().problem());
            addProblem(warning, p.question().warning());
            addProblem(warning, p.warning());
            for (DocxQuestionParser.Cell c : p.options()) {
                opts.add(new Opt(c.text(), c.image()));
                addProblem(problem, c.problem());
                addProblem(warning, c.warning());
            }
            Item it = new Item(p.qNo(), p.question().text(), p.question().image(), opts, p.correct(), p.marks(), p.co(), p.difficulty(), problem.length() == 0 ? null : problem.toString(),
                    p.bloom(), p.unit(), warning.length() == 0 ? null : warning.toString(), blank(p.explanation()), blank(p.category()), blank(p.tags()));
            insert(sid, p.rowNo(), it);
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Question Bank style rows (question, options separated by |, correct as numbers) turned into paper rows. Only MCQ types fit a paper. */
    private List<DocxQuestionParser.ParsedRow> fromTabular(List<Map<String, String>> rows, String subjectCode, AppException notTemplate) {
        List<DocxQuestionParser.ParsedRow> out = new ArrayList<>();
        int line = 1;
        for (Map<String, String> r : rows) {
            line++;
            String question = val(r, "question");
            if (notTemplate != null && question.isEmpty() && !r.containsKey("options")) {
                throw notTemplate;
            }
            String type = val(r, "type").toUpperCase(Locale.ROOT);
            String code = val(r, "subject_code");
            String problem = null;
            if (!type.isEmpty() && !type.equals("MCQ_SINGLE") && !type.equals("MCQ_MULTI")) {
                problem = "Only multiple-choice questions can be held in a paper (this row is " + type + "). Use \"Import directly\" on the Question Bank import page for other types.";
            } else if (!code.isEmpty() && !code.equalsIgnoreCase(subjectCode)) {
                problem = "This row belongs to subject " + code + ", but the paper is for " + subjectCode + ".";
            }
            List<DocxQuestionParser.Cell> opts = new ArrayList<>();
            String optionText = val(r, "options");
            if (!optionText.isEmpty()) {
                for (String o : optionText.split("\\|")) {
                    opts.add(new DocxQuestionParser.Cell(o.trim(), null, null));
                }
            }
            for (int i = 0; i < DocxQuestionParser.OPTION_LETTERS.length; i++) {
                String o = val(r, "option_" + DocxQuestionParser.OPTION_LETTERS[i].toLowerCase(Locale.ROOT));
                if (!o.isEmpty() && optionText.isEmpty()) {
                    while (opts.size() < i) {
                        opts.add(new DocxQuestionParser.Cell("", null, null));
                    }
                    opts.add(new DocxQuestionParser.Cell(o, null, null));
                }
            }
            if (opts.size() > DocxQuestionParser.OPTION_LETTERS.length && problem == null) {
                problem = "A paper can hold at most " + DocxQuestionParser.OPTION_LETTERS.length + " options per question.";
            }
            while (opts.size() > DocxQuestionParser.OPTION_LETTERS.length) {
                opts.remove(opts.size() - 1);
            }
            out.add(new DocxQuestionParser.ParsedRow(line, String.valueOf(line - 1), new DocxQuestionParser.Cell(question, null, problem), opts, numbersToLetters(val(r, "correct")),
                    val(r, "marks"), val(r, "co"), val(r, "difficulty"), val(r, "bloom"), val(r, "unit"), null, val(r, "explanation"), val(r, "category"), val(r, "tags")));
        }
        return out;
    }

    private static String val(Map<String, String> r, String key) {
        String v = r.get(key);
        return v == null ? "" : v.trim();
    }

    /** "2" -> "B", "1,3" -> "A,C"; letters are kept as they are. */
    static String numbersToLetters(String correct) {
        if (correct == null || correct.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : correct.split("[,;\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            String piece = part.trim();
            if (piece.matches("[1-6]")) {
                piece = DocxQuestionParser.OPTION_LETTERS[Integer.parseInt(piece) - 1];
            }
            sb.append(sb.length() == 0 ? "" : ",").append(piece);
        }
        return sb.toString();
    }

    private static void addProblem(StringBuilder sb, String p) {
        if (p != null) {
            sb.append(sb.length() == 0 ? "" : " ").append(p);
        }
    }

    private void insert(Long sid, int rowNo, Item it) {
        jdbc.sql("insert into upload_items (session_id, row_no, status, keep, payload) values (:s, :r, 'VALID', true, :p)")
                .param("s", sid).param("r", rowNo).param("p", write(it)).update();
    }

    private String write(Item it) {
        try {
            return json.writeValueAsString(it);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Item read(String payload) {
        try {
            return json.readValue(payload, Item.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ validation + duplicates
    /** Returns an error message, or null when the row is a valid MCQ. */
    String validate(Item it, Long subjectId) {
        if (it.problem() != null) {
            return it.problem();
        }
        if ((it.text() == null || it.text().isBlank()) && it.image() == null) {
            return "The question has neither text nor a picture.";
        }
        List<Opt> opts = it.options();
        int last = -1;
        for (int i = 0; i < opts.size(); i++) {
            if (filled(opts.get(i))) {
                last = i;
            }
        }
        if (last < 1) {
            return "At least two options (A and B) are required.";
        }
        for (int i = 0; i <= last; i++) {
            if (!filled(opts.get(i))) {
                return "Option " + DocxQuestionParser.OPTION_LETTERS[i] + " is missing (options must follow each other without a gap).";
            }
        }
        List<Integer> right = correctIndexes(it.correct());
        if (right.isEmpty()) {
            return "The Correct column is empty or invalid. Write the letter of the right option, for example B, or A,C for several.";
        }
        for (int i : right) {
            if (i > last) {
                return "The correct answer " + DocxQuestionParser.OPTION_LETTERS[i] + " points to an option that does not exist.";
            }
        }
        if (it.marks() != null && !it.marks().isBlank()) {
            try {
                BigDecimal m = new BigDecimal(it.marks().trim());
                if (m.signum() <= 0 || m.compareTo(new BigDecimal("1000")) > 0) {
                    return "Marks must be between 0.01 and 1000.";
                }
            } catch (NumberFormatException e) {
                return "Marks \"" + it.marks() + "\" is not a number.";
            }
        }
        if (it.difficulty() != null && !it.difficulty().isBlank() && parseDifficulty(it.difficulty()) == null) {
            return "Difficulty must be Easy, Medium or Hard.";
        }
        if (it.co() != null && !it.co().isBlank() && copo.coIdByCode(subjectId, it.co()) == null) {
            return "Course Outcome \"" + it.co() + "\" is not defined for this subject.";
        }
        if ((it.co() == null || it.co().isBlank()) && copo.hasCos(subjectId)) {
            return "The CO column is empty. Every question must be tagged with a Course Outcome (a number from 1 to 10, for example 1 for CO1).";
        }
        if (it.bloom() != null && !it.bloom().isBlank()) {
            try {
                com.mindbridgex.assessment.domain.Bloom.parse(it.bloom());
            } catch (IllegalArgumentException ex) {
                return "Bloom's level must be a number from 1 to 6 (1 Remember, 2 Understand, 3 Apply, 4 Analyze, 5 Evaluate, 6 Create) or the level name.";
            }
        }
        return null;
    }

    private static boolean filled(Opt o) {
        return (o.text() != null && !o.text().isBlank()) || o.image() != null;
    }

    public static List<Integer> correctIndexes(String correct) {
        List<Integer> out = new ArrayList<>();
        if (correct == null) {
            return out;
        }
        String c = correct.toUpperCase(Locale.ROOT).replaceAll("OPTION", "").trim();
        if (!c.matches("[A-F](\\s*[,;/&\\s]?\\s*[A-F])*")) {
            return out;
        }
        for (char ch : c.toCharArray()) {
            if (ch >= 'A' && ch <= 'F' && !out.contains(ch - 'A')) {
                out.add(ch - 'A');
            }
        }
        return out;
    }

    private static Difficulty parseDifficulty(String s) {
        for (Difficulty d : Difficulty.values()) {
            if (d.name().equalsIgnoreCase(s.trim()) || d.getLabel().equalsIgnoreCase(s.trim())) {
                return d;
            }
        }
        return null;
    }

    private QuestionHasher.Hashes hashes(Item it) {
        List<String> tokens = new ArrayList<>();
        List<String> imgs = new ArrayList<>();
        String q = storage.sha256(it.image());
        if (q != null) {
            imgs.add("Q:" + q);
        }
        int n = lastFilled(it);
        for (int i = 0; i <= n; i++) {
            Opt o = it.options().get(i);
            tokens.add(o.text() == null ? "" : o.text());
            String oi = storage.sha256(o.image());
            if (oi != null) {
                imgs.add("O:" + oi);
            }
        }
        QuestionType type = correctIndexes(it.correct()).size() > 1 ? QuestionType.MCQ_MULTI : QuestionType.MCQ_SINGLE;
        String text = it.text() == null ? "" : it.text();
        return new QuestionHasher.Hashes(ContentHasher.contentHash(type.name(), text, tokens), imgs.isEmpty() ? null : ContentHasher.sha256(String.join("|", imgs)));
    }

    private static int lastFilled(Item it) {
        int last = -1;
        for (int i = 0; i < it.options().size(); i++) {
            if (filled(it.options().get(i))) {
                last = i;
            }
        }
        return last;
    }

    /** Recomputes ERROR / DUPLICATE / VALID for the whole session (also after an edit or a deleted row). */
    private void reevaluate(Long sid, Long subjectId) {
        record Raw(Long id, int rowNo, String status, boolean keep, String payload) {
        }
        List<Raw> raws = jdbc.sql("select id, row_no, status, keep, payload from upload_items where session_id = :s order by row_no")
                .param("s", sid).query((rs, n) -> new Raw(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getBoolean(4), rs.getString(5))).list();
        Map<String, Integer> seen = new HashMap<>();
        boolean savedPaper = "CONFIRMED".equals(jdbc.sql("select status from upload_sessions where id = :s").param("s", sid).query(String.class).single());
        for (Raw r : raws) {
            Item it = read(r.payload());
            String err = validate(it, subjectId);
            String status;
            String msg = null;
            String ch = null;
            String ih = null;
            if (err != null) {
                status = "ERROR";
                msg = err;
            } else {
                QuestionHasher.Hashes h = hashes(it);
                ch = h.content();
                ih = h.image();
                String key = ch + "|" + (ih == null ? "" : ih);
                List<Long> inBank = savedPaper ? List.of() : questions.findDuplicates(subjectId, ch, ih, null);
                if (seen.containsKey(key)) {
                    status = "DUPLICATE";
                    msg = "Same question and options as row " + seen.get(key) + " of this file.";
                } else if (!inBank.isEmpty()) {
                    status = "DUPLICATE";
                    msg = "Already in the Question Bank (question #" + inBank.get(0) + ").";
                } else {
                    status = "VALID";
                }
                seen.putIfAbsent(key, r.rowNo());
            }
            boolean keep = switch (status) {
                case "VALID" -> true;
                case "ERROR" -> false;
                default -> "DUPLICATE".equals(r.status()) && r.keep();
            };
            jdbc.sql("update upload_items set status = :st, message = :m, keep = :k, content_hash = :ch, image_hash = :ih where id = :id")
                    .param("st", status).param("m", msg).param("k", keep).param("ch", ch).param("ih", ih).param("id", r.id()).update();
        }
    }

    // ------------------------------------------------------------------ review
    @Transactional(readOnly = true)
    public Session view(AuthUser user, Long sessionId) {
        Map<String, Object> s = sessionRow(user, sessionId);
        List<Row> rows = jdbc.sql("select id, row_no, status, message, keep, payload, excluded from upload_items where session_id = :s order by row_no").param("s", sessionId)
                .query((rs, n) -> {
                    Item it = read(rs.getString(6));
                    List<Boolean> flags = new ArrayList<>();
                    List<Integer> right = correctIndexes(it.correct());
                    for (int i = 0; i < it.options().size(); i++) {
                        flags.add(right.contains(i));
                    }
                    String status = rs.getString(3);
                    boolean keep = rs.getBoolean(5);
                    boolean excluded = rs.getBoolean(7);
                    boolean eligible = !"ERROR".equals(status) && !excluded && (!"DUPLICATE".equals(status) || keep);
                    return new Row(rs.getLong(1), rs.getInt(2), status, rs.getString(4), keep, it, flags, right.size() > 1 ? "Multiple answers" : "Single answer",
                            contentOf(it), validationOf(status, rs.getString(4), it), excluded, eligible);
                }).list();
        int valid = 0, dup = 0, err = 0;
        for (Row r : rows) {
            switch (r.status()) {
                case "VALID" -> valid++;
                case "DUPLICATE" -> dup++;
                default -> err++;
            }
        }
        return new Session(sessionId, (Long) s.get("exam_id"), (String) s.get("file_name"), (String) s.get("status"), rows, rows.size(), valid, dup, err,
                (Long) s.get("subject_id"), (String) s.get("subject_name"), (String) s.get("source"), metrics(rows));
    }

    /** Text, Image or Mixed (text together with a picture on the question or on any option). */
    static String contentOf(Item it) {
        boolean text = it.text() != null && !it.text().isBlank() && !QuestionService.IMAGE_ONLY_TEXT.equals(it.text().trim());
        boolean picture = it.image() != null || it.options().stream().anyMatch(o -> o.image() != null);
        return text ? (picture ? "Mixed" : "Text") : (picture ? "Image" : "Text");
    }

    /** VALID, WARNING, NEEDS_REVIEW (the answer is missing) or INVALID. Duplicate status is separate. */
    static String validationOf(String status, String message, Item it) {
        if ("ERROR".equals(status)) {
            return message != null && message.startsWith("The Correct column") ? "NEEDS_REVIEW" : "INVALID";
        }
        return it.warning() != null ? "WARNING" : "VALID";
    }

    static Metrics metrics(List<Row> rows) {
        int valid = 0, warn = 0, review = 0, invalid = 0, dup = 0, eligible = 0, noAnswer = 0, noOptions = 0, images = 0, imageIssues = 0;
        for (Row r : rows) {
            switch (r.vstatus()) {
                case "VALID" -> valid++;
                case "WARNING" -> warn++;
                case "NEEDS_REVIEW" -> review++;
                default -> invalid++;
            }
            if ("DUPLICATE".equals(r.status())) {
                dup++;
            }
            if (r.eligible()) {
                eligible++;
            }
            String m = r.message() == null ? "" : r.message();
            if ("ERROR".equals(r.status())) {
                if (m.startsWith("The Correct column") || m.contains("points to an option that does not exist")) {
                    noAnswer++;
                }
                if (m.startsWith("At least two options") || m.contains("is missing (options must")) {
                    noOptions++;
                }
            }
            if (r.item().image() != null || r.item().options().stream().anyMatch(o -> o.image() != null)) {
                images++;
            }
            String problem = r.item().problem() == null ? "" : r.item().problem().toLowerCase(Locale.ROOT);
            String warning = r.item().warning() == null ? "" : r.item().warning().toLowerCase(Locale.ROOT);
            if (problem.contains("picture") || warning.contains("picture") || warning.contains("image")) {
                imageIssues++;
            }
        }
        return new Metrics(rows.size(), valid, warn, review, invalid, dup, eligible, eligible, rows.size() - eligible, noAnswer, noOptions, images, imageIssues);
    }

    /** The paper's row, after checking that this teacher may see it (the exam's rules for an exam paper, otherwise: the uploader). */
    private Map<String, Object> sessionRow(AuthUser user, Long sessionId) {
        List<Map<String, Object>> found = jdbc.sql("select s.exam_id, s.file_name, s.status, s.subject_id, s.source, s.created_by, sub.name as subject_name "
                + "from upload_sessions s join subjects sub on sub.id = s.subject_id where s.id = :id").param("id", sessionId).query().listOfRows();
        if (found.isEmpty()) {
            throw AppException.notFound("Paper");
        }
        Map<String, Object> s = found.get(0);
        if (s.get("exam_id") != null) {
            exams.get(user, (Long) s.get("exam_id"));
        } else if (!(s.get("created_by") instanceof Number owner) || owner.longValue() != user.getId()) {
            throw AppException.notFound("Paper");
        }
        return s;
    }

    private Long subjectOf(AuthUser user, Long sessionId) {
        Map<String, Object> s = sessionRow(user, sessionId);
        // a paper (not tied to an exam) is a saved list of questions: it stays editable after it was added to the bank, so it can be sent again
        boolean savedPaper = s.get("exam_id") == null && "CONFIRMED".equals(s.get("status"));
        if (!"PENDING".equals(s.get("status")) && !savedPaper) {
            throw AppException.conflict("This paper has already been " + ((String) s.get("status")).toLowerCase(Locale.ROOT) + ".");
        }
        if (s.get("exam_id") != null) {
            return exams.requireEditable(user, (Long) s.get("exam_id")).getSubject().getId();
        }
        access.requireSubject(user, (Long) s.get("subject_id"));
        return (Long) s.get("subject_id");
    }

    public record Edit(String text, List<String> optionTexts, String correct, String marks, String co, String difficulty, String bloom, String unit, MultipartFile questionImage, List<MultipartFile> optionImages,
                       boolean removeQuestionImage, List<Integer> removeOptionImages) {
    }

    @Transactional
    public void edit(AuthUser user, Long sessionId, Long itemId, Edit e) {
        Long subjectId = subjectOf(user, sessionId);
        String payload = jdbc.sql("select payload from upload_items where id = :i and session_id = :s").param("i", itemId).param("s", sessionId).query(String.class)
                .optional().orElseThrow(() -> AppException.notFound("Row"));
        Item old = read(payload);
        String qImg = old.image();
        if (e.removeQuestionImage()) {
            qImg = null;
        }
        if (e.questionImage() != null && !e.questionImage().isEmpty()) {
            qImg = storage.storeImageOnly(e.questionImage());
        }
        List<Opt> opts = new ArrayList<>();
        for (int i = 0; i < DocxQuestionParser.OPTION_LETTERS.length; i++) {
            String t = e.optionTexts() != null && i < e.optionTexts().size() && e.optionTexts().get(i) != null ? e.optionTexts().get(i).trim() : "";
            String img = i < old.options().size() ? old.options().get(i).image() : null;
            if (e.removeOptionImages() != null && e.removeOptionImages().contains(i)) {
                img = null;
            }
            if (e.optionImages() != null && i < e.optionImages().size() && e.optionImages().get(i) != null && !e.optionImages().get(i).isEmpty()) {
                img = storage.storeImageOnly(e.optionImages().get(i));
            }
            opts.add(new Opt(t, img));
        }
        Item updated = new Item(old.qNo(), e.text() == null ? "" : e.text().trim(), qImg, opts, e.correct() == null ? "" : e.correct().trim(),
                e.marks() == null ? "" : e.marks().trim(), e.co() == null ? "" : e.co().trim(), e.difficulty() == null ? "" : e.difficulty().trim(), null, e.bloom() == null ? "" : e.bloom().trim(), e.unit() == null ? "" : e.unit().trim(),
                null, old.explanation(), old.category(), old.tags());
        jdbc.sql("update upload_items set payload = :p where id = :i").param("p", write(updated)).param("i", itemId).update();
        reevaluate(sessionId, subjectId);
        Set<String> gone = imageNames(payload);
        gone.removeAll(imageNames(write(updated)));
        deleteWhenUnreferenced(gone);
    }

    @Transactional
    public void deleteItem(AuthUser user, Long sessionId, Long itemId) {
        Long subjectId = subjectOf(user, sessionId);
        Set<String> names = new java.util.LinkedHashSet<>();
        jdbc.sql("select payload from upload_items where id = :i and session_id = :s").param("i", itemId).param("s", sessionId).query(String.class).list().forEach(pl -> names.addAll(imageNames(pl)));
        jdbc.sql("delete from upload_items where id = :i and session_id = :s").param("i", itemId).param("s", sessionId).update();
        reevaluate(sessionId, subjectId);
        deleteWhenUnreferenced(names);
    }

    @Transactional
    public void setKeep(AuthUser user, Long sessionId, Long itemId, boolean keep) {
        subjectOf(user, sessionId);
        jdbc.sql("update upload_items set keep = :k where id = :i and session_id = :s and status = 'DUPLICATE'").param("k", keep).param("i", itemId).param("s", sessionId).update();
    }

    @Transactional
    public void discard(AuthUser user, Long sessionId) {
        subjectOf(user, sessionId);
        Set<String> names = new java.util.LinkedHashSet<>();
        jdbc.sql("select payload from upload_items where session_id = :s").param("s", sessionId).query(String.class).list().forEach(pl -> names.addAll(imageNames(pl)));
        jdbc.sql("delete from upload_items where session_id = :s").param("s", sessionId).update();
        jdbc.sql("update upload_sessions set status = 'DISCARDED' where id = :s").param("s", sessionId).update();
        deleteWhenUnreferenced(names);
        audit.log("UPLOAD_DISCARD", "UploadSession", sessionId, "paper " + sessionId);
    }

    // ------------------------------------------------------------------ confirm / merge into the bank
    /** Exam paper: saves the kept questions to the Question Bank AND adds them to the exam (the exam page's Confirm). */
    public ConfirmResult confirm(AuthUser user, Long sessionId) {
        return saveToBank(user, sessionId, true);
    }

    /** Explicit "Merge into Question Bank": saves the kept questions to the bank only. */
    public ConfirmResult mergeToBank(AuthUser user, Long sessionId) {
        return saveToBank(user, sessionId, false);
    }

    private void releaseClaim(Long sessionId) {
        jdbc.sql("update upload_sessions set status = 'PENDING', merged_at = null where id = :s and status = 'CONFIRMED'").param("s", sessionId).update();
    }

    /** Not transactional on purpose: each question is saved on its own so one bad row cannot undo the others. */
    private ConfirmResult saveToBank(AuthUser user, Long sessionId, boolean addToExam) {
        Long subjectId = subjectOf(user, sessionId);
        Map<String, Object> s = sessionRow(user, sessionId);
        Long examId = (Long) s.get("exam_id");
        if (addToExam && examId == null) {
            throw AppException.bad("This paper is not attached to an exam. Use Merge into Question Bank.");
        }
        String file = (String) s.get("file_name");
        // the bank may have changed since this paper was uploaded (another paper merged, a question added): check duplicates again now
        if ("PENDING".equals(s.get("status"))) {
            reevaluate(sessionId, subjectId);
        }
        record Raw(Long id, String status, boolean keep, boolean excluded, String payload) {
        }
        List<Raw> rows = jdbc.sql("select id, status, keep, excluded, payload from upload_items where session_id = :s order by row_no").param("s", sessionId)
                .query((rs, n) -> new Raw(rs.getLong(1), rs.getString(2), rs.getBoolean(3), rs.getBoolean(4), rs.getString(5))).list();
        java.util.function.Predicate<Raw> eligible = r -> !"ERROR".equals(r.status()) && !r.excluded() && !("DUPLICATE".equals(r.status()) && !r.keep());
        if (rows.stream().noneMatch(eligible)) {
            throw AppException.bad("Nothing to merge: no question of this paper is selected and valid. Fix, edit or include questions first.");
        }
        // claim the paper first, so pressing the button twice (or two browser tabs) can never save the questions twice
        if (jdbc.sql("update upload_sessions set status = 'CONFIRMED', merged_at = now() where id = :s and status = 'PENDING'").param("s", sessionId).update() == 0) {
            throw AppException.conflict("This paper has already been merged.");
        }
        List<Long> created = new ArrayList<>();
        int skipped = 0;
        int failed = 0;
        String source = "bulk upload: " + file + " (" + java.time.LocalDate.now() + ")";
        try {
            for (Raw r : rows) {
                if (!eligible.test(r)) {
                    skipped++;
                    continue;
                }
                try {
                    Question q = questionService.save(user, toForm(read(r.payload()), subjectId, source));
                    created.add(q.getId());
                } catch (RuntimeException e) {
                    failed++;
                }
            }
            if (addToExam && !created.isEmpty()) {
                exams.addQuestions(user, examId, created);
            }
        } catch (RuntimeException e) {
            releaseClaim(sessionId);
            throw e;
        }
        if (created.isEmpty()) {
            releaseClaim(sessionId); // nothing could be saved: keep it as a draft so the teacher can fix it
        }
        audit.log(addToExam ? "UPLOAD_CONFIRM" : "PAPER_MERGE_BANK", examId == null ? "Subject" : "Exam", examId == null ? subjectId : examId,
                file + ": " + created.size() + " question(s) saved to the bank" + (addToExam ? " and exam" : "") + ", " + skipped + " skipped, " + failed + " failed");
        return new ConfirmResult(created.size(), skipped, failed);
    }

    /** Brings the duplicate flags of a draft paper up to date with the Question Bank as it is now. */
    @Transactional
    public void recheck(AuthUser user, Long sessionId) {
        Map<String, Object> row = sessionRow(user, sessionId);
        if (!"PENDING".equals(row.get("status")) && !(row.get("exam_id") == null && "CONFIRMED".equals(row.get("status")))) {
            return; // finished exam uploads and deleted papers are only shown, never changed
        }
        reevaluate(sessionId, subjectOf(user, sessionId));
    }

    /** The name of another paper of this teacher (same subject) that holds exactly the same questions, or null. */
    @Transactional(readOnly = true)
    public String sameAs(AuthUser user, Long sessionId) {
        SamePaper p = sameAsPaper(user, sessionId);
        return p == null ? null : p.name();
    }

    public record SamePaper(Long id, String name) {
    }

    /** The other paper of this teacher (same subject) that holds exactly the same questions, or null. */
    @Transactional(readOnly = true)
    public SamePaper sameAsPaper(AuthUser user, Long sessionId) {
        Long subjectId = subjectOf(user, sessionId);
        record H(Long sid, String name, String key) {
        }
        List<H> all = jdbc.sql("select i.session_id, u.file_name, i.content_hash || '|' || coalesce(i.image_hash, '') from upload_items i join upload_sessions u on u.id = i.session_id "
                + "where u.subject_id = :sub and u.created_by = :u and u.status <> 'DISCARDED' and i.content_hash is not null")
                .param("sub", subjectId).param("u", user.getId()).query((rs, n) -> new H(rs.getLong(1), rs.getString(2), rs.getString(3))).list();
        Map<Long, java.util.TreeSet<String>> bySession = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (H h : all) {
            bySession.computeIfAbsent(h.sid(), k -> new java.util.TreeSet<>()).add(h.key());
            names.put(h.sid(), h.name());
        }
        java.util.TreeSet<String> mine = bySession.get(sessionId);
        if (mine == null || mine.isEmpty()) {
            return null;
        }
        for (Map.Entry<Long, java.util.TreeSet<String>> e : bySession.entrySet()) {
            if (!e.getKey().equals(sessionId) && e.getValue().equals(mine)) {
                return new SamePaper(e.getKey(), names.get(e.getKey()));
            }
        }
        return null;
    }

    /** Adds multiple-choice questions of the same subject from the Question Bank to this paper. Returns how many were added. */
    @Transactional
    public int addFromBank(AuthUser user, Long sessionId, List<Long> questionIds) {
        Long subjectId = subjectOf(user, sessionId);
        if (questionIds == null || questionIds.isEmpty()) {
            throw AppException.bad("Tick at least one question first.");
        }
        int next = jdbc.sql("select coalesce(max(row_no), 0) from upload_items where session_id = :s").param("s", sessionId).query(Integer.class).single();
        int added = 0;
        for (Question q : questions.findAllById(questionIds)) {
            boolean mcq = q.getType() == QuestionType.MCQ_SINGLE || q.getType() == QuestionType.MCQ_MULTI;
            if (!q.getSubject().getId().equals(subjectId) || q.isArchived() || !mcq) {
                continue;
            }
            List<Opt> opts = new ArrayList<>();
            StringBuilder correct = new StringBuilder();
            int i = 0;
            for (com.mindbridgex.assessment.entity.QuestionOption o : q.getOptions()) {
                opts.add(new Opt(o.getText(), o.getImagePath()));
                if (o.isCorrect() && i < DocxQuestionParser.OPTION_LETTERS.length) {
                    correct.append(correct.length() == 0 ? "" : ",").append(DocxQuestionParser.OPTION_LETTERS[i]);
                }
                i++;
            }
            String co = q.getCoId() == null ? null : jdbc.sql("select code from course_outcomes where id = :i").param("i", q.getCoId()).query(String.class).optional().orElse(null);
            Item it = new Item(null, q.getText(), q.getImagePath(), opts, correct.toString(), q.getDefaultMarks().stripTrailingZeros().toPlainString(), co, q.getDifficulty().getLabel(), null,
                    q.getBloom() == null ? null : q.getBloom().getLabel(), q.getUnit(), null, q.getExplanation(), q.getCategory() == null ? null : q.getCategory().getName(),
                    q.getTags().isEmpty() ? null : String.join(",", q.getTags()));
            insert(sessionId, ++next, it);
            added++;
        }
        if (added == 0) {
            throw AppException.bad("None of the ticked questions could be added (only multiple-choice questions of this subject can).");
        }
        reevaluate(sessionId, subjectId);
        audit.log("PAPER_ADD_FROM_BANK", "UploadSession", sessionId, added + " question(s)");
        return added;
    }

    public record BatchPick(String name, String studentIds) {
    }

    /** Active batches that have students, for the "quick pick a batch" buttons of the publish page. */
    @Transactional(readOnly = true)
    public List<BatchPick> batchPicks() {
        return jdbc.sql("select b.name, string_agg(bs.student_id::text, ',') from batches b join batch_students bs on bs.batch_id = b.id where b.active = true group by b.id, b.name order by b.name")
                .query((rs, n) -> new BatchPick(rs.getString(1), rs.getString(2))).list();
    }

    public record PublishForm(String title, String instructions, int durationMinutes, java.time.LocalDateTime startAt, java.time.LocalDateTime endAt,
                              java.math.BigDecimal passingMarks, List<Long> batchIds, String students) {
    }

    public record PublishResult(Long examId, boolean published, String message, int questions, int newInBank) {
    }

    /**
     * Turns a paper into an exam for students: its questions go to the Question Bank (a question that is already there is reused,
     * never copied), an exam is created from them, assigned to the chosen batches / students and published.
     * If the exam cannot be published yet (for example it needs approval), it is left as a draft and the reason is returned.
     */
    @Transactional
    public PublishResult publishPaper(AuthUser user, Long sessionId, PublishForm f) {
        Long subjectId = subjectOf(user, sessionId);
        Map<String, Object> s = sessionRow(user, sessionId);
        String status = (String) s.get("status");
        if ("DISCARDED".equals(status)) {
            throw AppException.bad("This paper was deleted.");
        }
        if (f.title() == null || f.title().isBlank()) {
            throw AppException.bad("Give the exam a title.");
        }
        if (f.startAt() == null || f.endAt() == null) {
            throw AppException.bad("Choose when the exam opens and closes.");
        }
        boolean noBatches = f.batchIds() == null || f.batchIds().isEmpty();
        boolean noStudents = f.students() == null || f.students().isBlank();
        if (noBatches && noStudents) {
            throw AppException.bad("Pick at least one student, otherwise nobody can see the exam.");
        }
        reevaluate(sessionId, subjectId);
        record Raw(String payload, String ch, String ih) {
        }
        List<Raw> rows = jdbc.sql("select payload, content_hash, image_hash from upload_items where session_id = :s and status <> 'ERROR' and excluded = false order by row_no")
                .param("s", sessionId).query((rs, n) -> new Raw(rs.getString(1), rs.getString(2), rs.getString(3))).list();
        if (rows.isEmpty()) {
            throw AppException.bad("Nothing to publish: this paper has no valid question that is included.");
        }
        String source = "bulk upload: " + s.get("file_name") + " (" + java.time.LocalDate.now() + ")";
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        int created = 0;
        for (Raw r : rows) {
            List<Long> existing = questions.findDuplicates(subjectId, r.ch(), r.ih(), null);
            if (!existing.isEmpty()) {
                ids.add(existing.get(0));
            } else {
                ids.add(questionService.save(user, toForm(read(r.payload()), subjectId, source)).getId());
                created++;
            }
        }
        if ("PENDING".equals(status)) {
            jdbc.sql("update upload_sessions set status = 'CONFIRMED', merged_at = now() where id = :s and status = 'PENDING'").param("s", sessionId).update();
        }
        com.mindbridgex.assessment.dto.ExamForm ef = new com.mindbridgex.assessment.dto.ExamForm();
        ef.setSubjectId(subjectId);
        ef.setTitle(f.title().trim());
        ef.setInstructions(f.instructions());
        ef.setDurationMinutes(f.durationMinutes() <= 0 ? 60 : f.durationMinutes());
        ef.setStartAt(f.startAt());
        ef.setEndAt(f.endAt());
        ef.setPassingMarks(f.passingMarks() == null ? java.math.BigDecimal.ZERO : f.passingMarks());
        com.mindbridgex.assessment.entity.Exam exam = exams.save(user, ef);
        exams.addQuestions(user, exam.getId(), ids);
        if (!noBatches) {
            exams.assignBatches(user, exam.getId(), f.batchIds());
        }
        String missing = "";
        if (!noStudents) {
            List<String> unknown = exams.assignStudents(user, exam.getId(), f.students());
            if (!unknown.isEmpty()) {
                missing = " These usernames were not found: " + String.join(", ", unknown) + ".";
            }
        }
        audit.log("PAPER_PUBLISH", "Exam", exam.getId(), s.get("file_name") + ": " + ids.size() + " question(s), " + created + " new in the bank");
        // publishing a paper is the teacher's own decision: no separate approval step for exams created this way
        exam.setApprovalStatus("APPROVED");
        // ask first instead of catching an exception: a failed call inside this transaction would roll the whole thing back
        List<String> problems = exams.detail(user, exam.getId()).problems();
        if (!problems.isEmpty()) {
            return new PublishResult(exam.getId(), false, "The exam was created but is not published yet. " + String.join(" ", problems) + missing, ids.size(), created);
        }
        exams.publish(user, exam.getId());
        return new PublishResult(exam.getId(), true, "Published to students." + missing, ids.size(), created);
    }

    // ------------------------------------------------------------------ dashboard actions
    /** Leaves questions out of (or puts them back into) the merge. Independent of validation and duplicate status. */
    @Transactional
    public void setExcluded(AuthUser user, Long sessionId, List<Long> itemIds, boolean excluded) {
        subjectOf(user, sessionId);
        if (itemIds == null || itemIds.isEmpty()) {
            throw AppException.bad("Select at least one question first.");
        }
        jdbc.sql("update upload_items set excluded = :x where session_id = :s and id in (:ids)").param("x", excluded).param("s", sessionId).param("ids", itemIds).update();
    }

    /**
     * Moves a picture between the question and its options ("Q", "A" to "F") when the importer put it in the wrong place.
     * If the target already has a picture the two are swapped, so nothing is ever lost.
     */
    @Transactional
    public void moveImage(AuthUser user, Long sessionId, Long itemId, String from, String to) {
        Long subjectId = subjectOf(user, sessionId);
        String payload = jdbc.sql("select payload from upload_items where id = :i and session_id = :s").param("i", itemId).param("s", sessionId).query(String.class)
                .optional().orElseThrow(() -> AppException.notFound("Row"));
        Item it = read(payload);
        int a = slot(from);
        int b = slot(to);
        if (a == b) {
            throw AppException.bad("Choose two different places.");
        }
        String[] images = new String[1 + DocxQuestionParser.OPTION_LETTERS.length];
        images[0] = it.image();
        List<Opt> opts = new ArrayList<>(it.options());
        while (opts.size() < DocxQuestionParser.OPTION_LETTERS.length) {
            opts.add(new Opt("", null));
        }
        for (int i = 0; i < DocxQuestionParser.OPTION_LETTERS.length; i++) {
            images[i + 1] = opts.get(i).image();
        }
        if (images[a] == null) {
            throw AppException.bad("There is no picture in the place you are moving from.");
        }
        String tmp = images[a];
        images[a] = images[b];
        images[b] = tmp;
        List<Opt> moved = new ArrayList<>();
        for (int i = 0; i < DocxQuestionParser.OPTION_LETTERS.length; i++) {
            moved.add(new Opt(opts.get(i).text(), images[i + 1]));
        }
        Item updated = new Item(it.qNo(), it.text(), images[0], moved, it.correct(), it.marks(), it.co(), it.difficulty(), null, it.bloom(), it.unit(), null, it.explanation(), it.category(), it.tags());
        jdbc.sql("update upload_items set payload = :p where id = :i").param("p", write(updated)).param("i", itemId).update();
        reevaluate(sessionId, subjectId);
    }

    private static int slot(String place) {
        if ("Q".equalsIgnoreCase(place)) {
            return 0;
        }
        if (place != null && place.length() == 1 && place.toUpperCase(Locale.ROOT).charAt(0) >= 'A' && place.toUpperCase(Locale.ROOT).charAt(0) < 'A' + DocxQuestionParser.OPTION_LETTERS.length) {
            return 1 + place.toUpperCase(Locale.ROOT).charAt(0) - 'A';
        }
        throw AppException.bad("Unknown place for the picture.");
    }

    // ------------------------------------------------------------------ orphaned pictures
    private static final java.util.regex.Pattern IMAGE_NAME = java.util.regex.Pattern.compile("[a-f0-9-]{36}\\.(?:png|jpg|gif|webp)");

    private static Set<String> imageNames(String payload) {
        Set<String> out = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = IMAGE_NAME.matcher(payload == null ? "" : payload);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** Deletes picture files that were only held by the staging rows that just went away (never one used by another paper, a question, an option or an attempt). */
    private void deleteWhenUnreferenced(Set<String> names) {
        if (names.isEmpty()) {
            return;
        }
        Runnable job = () -> {
            for (String n : names) {
                String like = "%" + n + "%";
                boolean used = Boolean.TRUE.equals(jdbc.sql("select exists (select 1 from upload_items where payload like :l) "
                        + "or exists (select 1 from questions where image_path = :n) or exists (select 1 from question_options where image_path = :n) "
                        + "or exists (select 1 from attempt_questions where image_path = :n or options::text like :l)").param("l", like).param("n", n).query(Boolean.class).single());
                if (!used) {
                    storage.deleteQuietly(n);
                }
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    job.run();
                }
            });
        } else {
            job.run();
        }
    }

    // ------------------------------------------------------------------ the "Question papers" list
    @Transactional(readOnly = true)
    public List<Paper> papers(AuthUser user) {
        return jdbc.sql("select s.id, s.file_name, sub.name, s.exam_id, e.title, s.status, s.source, s.created_at, "
                + "(select count(*) from upload_items i where i.session_id = s.id), "
                + "(select count(*) from upload_items i where i.session_id = s.id and i.status = 'VALID'), "
                + "(select count(*) from upload_items i where i.session_id = s.id and i.status = 'DUPLICATE'), "
                + "(select count(*) from upload_items i where i.session_id = s.id and i.status = 'ERROR') "
                + "from upload_sessions s join subjects sub on sub.id = s.subject_id left join exams e on e.id = s.exam_id "
                + "where s.created_by = :u and s.status <> 'DISCARDED' order by s.created_at desc, s.id desc")
                .param("u", user.getId())
                .query((rs, n) -> new Paper(rs.getLong(1), rs.getString(2), rs.getString(3), (Long) rs.getObject(4), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getTimestamp(8).toInstant(), rs.getInt(9), rs.getInt(10), rs.getInt(11), rs.getInt(12))).list();
    }

    // ------------------------------------------------------------------ merge two (or more) papers into a new one
    /** Combines draft papers of the same subject into one NEW draft paper (the originals are left as they are). Duplicates between them are flagged for review. */
    @Transactional
    public Long mergePapers(AuthUser user, List<Long> ids, String title) {
        List<Long> distinct = ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.size() < 2) {
            throw AppException.bad("Select at least two papers to merge.");
        }
        if (distinct.size() > 10) {
            throw AppException.bad("Merge at most 10 papers at a time.");
        }
        Long subjectId = null;
        List<String> names = new ArrayList<>();
        for (Long id : distinct) {
            Long sub = subjectOf(user, id);
            if (subjectId != null && !subjectId.equals(sub)) {
                throw AppException.bad("Only papers of the same subject can be merged.");
            }
            subjectId = sub;
            names.add((String) sessionRow(user, id).get("file_name"));
        }
        String name = title == null || title.isBlank() ? "Merged: " + String.join(" + ", names) : title.trim();
        name = name.length() > 250 ? name.substring(0, 250) : name;
        Long sid = jdbc.sql("insert into upload_sessions (exam_id, subject_id, file_name, source, created_by) values (null, :sub, :f, 'MERGE', :u) returning id")
                .param("sub", subjectId).param("f", name).param("u", user.getId()).query(Long.class).single();
        int rowNo = 0;
        for (Long id : distinct) {
            List<String> payloads = jdbc.sql("select payload from upload_items where session_id = :s order by row_no").param("s", id).query(String.class).list();
            for (String payload : payloads) {
                insert(sid, ++rowNo, withNumber(read(payload), rowNo));
            }
        }
        reevaluate(sid, subjectId);
        audit.log("PAPER_MERGE", "Subject", subjectId, name + ": " + rowNo + " row(s) from " + distinct.size() + " paper(s)");
        return sid;
    }

    private static Item withNumber(Item it, int no) {
        return new Item(String.valueOf(no), it.text(), it.image(), it.options(), it.correct(), it.marks(), it.co(), it.difficulty(), it.problem(), it.bloom(), it.unit(),
                it.warning(), it.explanation(), it.category(), it.tags());
    }

    // ------------------------------------------------------------------ Word round trip
    /** The paper as a Word file in the standard template (pictures included), ready to be edited in Word and uploaded back. */
    @Transactional(readOnly = true)
    public byte[] exportDocx(AuthUser user, Long sessionId) {
        return docxWriter.write(view(user, sessionId));
    }

    /** Replaces every row of a draft paper with the rows of an edited Word file. */
    @Transactional
    public void replaceFromDocx(AuthUser user, Long sessionId, MultipartFile file) {
        Long subjectId = subjectOf(user, sessionId);
        String name = fileName(file);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw AppException.bad("Upload the Word (.docx) file you downloaded from this paper.");
        }
        List<DocxQuestionParser.ParsedRow> parsed = parseDocx(file);
        if (parsed.isEmpty()) {
            throw AppException.bad("The Word file has no question rows, so nothing was replaced.");
        }
        Set<String> before = new java.util.LinkedHashSet<>();
        jdbc.sql("select payload from upload_items where session_id = :s").param("s", sessionId).query(String.class).list().forEach(pl -> before.addAll(imageNames(pl)));
        jdbc.sql("delete from upload_items where session_id = :s").param("s", sessionId).update();
        insertParsed(sessionId, parsed);
        reevaluate(sessionId, subjectId);
        deleteWhenUnreferenced(before);
        audit.log("PAPER_REPLACE", "UploadSession", sessionId, name + ": " + parsed.size() + " row(s)");
    }

    private QuestionForm toForm(Item it, Long subjectId, String source) {
        QuestionForm f = new QuestionForm();
        f.setSubjectId(subjectId);
        List<Integer> right = correctIndexes(it.correct());
        f.setType(right.size() > 1 ? QuestionType.MCQ_MULTI : QuestionType.MCQ_SINGLE);
        f.setText(it.text() == null || it.text().isBlank() ? "[Image question]" : it.text().trim());
        f.setExistingImage(it.image());
        Difficulty d = it.difficulty() == null || it.difficulty().isBlank() ? null : parseDifficulty(it.difficulty());
        f.setDifficulty(d == null ? Difficulty.MEDIUM : d);
        f.setDefaultMarks(it.marks() == null || it.marks().isBlank() ? BigDecimal.ONE : new BigDecimal(it.marks().trim()));
        f.setCoId(copo.coIdByCode(subjectId, it.co()));
        f.setSource(source);
        f.setBloom(it.bloom() == null || it.bloom().isBlank() ? null : com.mindbridgex.assessment.domain.Bloom.parse(it.bloom()));
        f.setUnit(it.unit() == null || it.unit().isBlank() ? null : it.unit().trim());
        f.setAllowDuplicate(true);
        f.setExplanation(it.explanation());
        f.setTags(it.tags());
        if (it.category() != null && !it.category().isBlank()) {
            String cat = it.category().trim();
            f.setCategoryId(categories.findBySubjectIdAndNameIgnoreCase(subjectId, cat).orElseGet(() -> {
                com.mindbridgex.assessment.entity.QuestionCategory n = new com.mindbridgex.assessment.entity.QuestionCategory();
                n.setSubject(subjects.getReferenceById(subjectId));
                n.setName(cat.length() > 100 ? cat.substring(0, 100) : cat);
                return categories.save(n);
            }).getId());
        }
        for (int i = 0; i <= lastFilled(it); i++) {
            Opt o = it.options().get(i);
            OptionForm of = new OptionForm(null, o.text() == null ? "" : o.text().trim(), null, right.contains(i));
            of.setImagePath(o.image());
            f.getOptions().add(of);
        }
        return f;
    }
}
