package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.domain.Difficulty;
import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.dto.OptionForm;
import com.mindbridgex.assessment.dto.QuestionForm;
import com.mindbridgex.assessment.entity.Question;
import com.mindbridgex.assessment.entity.QuestionCategory;
import com.mindbridgex.assessment.entity.QuestionOption;
import com.mindbridgex.assessment.entity.Subject;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.QuestionCategoryRepository;
import com.mindbridgex.assessment.repository.QuestionRepository;
import com.mindbridgex.assessment.repository.QuestionSpecs;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.util.TabularReader;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bulk import / export of the question bank (.xlsx or .csv).
 * Columns: subject_code, category, type, difficulty, marks, question, options, correct, tolerance, case_sensitive,
 * partial, explanation, tags. Options are separated by "|"; matching pairs are written "left=right".
 */
@Service
public class QuestionImportExportService {
    public static final int MAX_ROWS = 1000;
    public static final List<String> COLUMNS = List.of("subject_code", "category", "type", "difficulty", "marks", "question", "options", "correct", "tolerance", "case_sensitive", "partial", "explanation", "tags", "co", "bloom", "unit");


    public record RowError(int row, String message) {
    }


    public record ImportResult(int imported, List<RowError> errors) {
    }

    @org.springframework.beans.factory.annotation.Autowired
    private CoPoService copo;
    private final QuestionService questionService;
    private final AccessService access;
    private final QuestionCategoryRepository categories;
    private final QuestionRepository questions;
    private final AuditService audit;

    public ImportResult importFile(AuthUser user, MultipartFile file) {
        List<Map<String, String>> rows = TabularReader.read(file, MAX_ROWS);
        Map<String, Subject> subjectsByCode = new HashMap<>();
        access.teacherSubjects(user).forEach(s -> subjectsByCode.put(s.getCode().toLowerCase(Locale.ROOT), s));
        List<RowError> errors = new ArrayList<>();
        int ok = 0;
        int line = 1;
        for (Map<String, String> r : rows) {
            line++;
            try {
                QuestionForm form = toForm(user, r, subjectsByCode);
                form.setSource("bulk import: " + file.getOriginalFilename() + " (" + java.time.LocalDate.now() + ")");
                String co = get(r, "co");
                if (!co.isEmpty()) {
                    Long coId = copo.coIdByCode(form.getSubjectId(), co);
                    if (coId == null) {
                        throw AppException.bad("Unknown Course Outcome \'" + co + "\' for this subject.");
                    }
                    form.setCoId(coId);
                }
                String bl = get(r, "bloom");
                if (!bl.isEmpty()) {
                    try {
                        form.setBloom(com.mindbridgex.assessment.domain.Bloom.parse(bl));
                    } catch (IllegalArgumentException ex) {
                        throw AppException.bad("Bloom's level must be Remember, Understand, Apply, Analyze, Evaluate or Create.");
                    }
                }
                form.setUnit(get(r, "unit").isEmpty() ? null : get(r, "unit"));
                questionService.save(user, form);
                ok++;
            } catch (AppException e) {
                errors.add(new RowError(line, e.getMessage()));
            } catch (RuntimeException e) {
                errors.add(new RowError(line, "Could not import this row: " + e.getClass().getSimpleName()));
            }
        }
        audit.log("QUESTION_IMPORT", "Question", null, ok + " imported, " + errors.size() + " rejected");
        return new ImportResult(ok, errors);
    }

    private QuestionForm toForm(AuthUser user, Map<String, String> r, Map<String, Subject> subjects) {
        QuestionForm f = new QuestionForm();
        String code = get(r, "subject_code");
        Subject s = subjects.get(code.toLowerCase(Locale.ROOT));
        if (s == null) {
            throw AppException.bad("Unknown or unassigned subject code \'" + code + "\'.");
        }
        f.setSubjectId(s.getId());
        String cat = get(r, "category");
        if (!cat.isEmpty()) {
            QuestionCategory c = categories.findBySubjectIdAndNameIgnoreCase(s.getId(), cat).orElseGet(() -> {
                QuestionCategory n = new QuestionCategory();
                n.setSubject(s);
                n.setName(cat);
                return categories.save(n);
            });
            f.setCategoryId(c.getId());
        }
        f.setType(parseType(get(r, "type")));
        String diff = get(r, "difficulty");
        f.setDifficulty(diff.isEmpty() ? Difficulty.MEDIUM : parseEnum(Difficulty.class, diff, "difficulty"));
        String marks = get(r, "marks");
        f.setDefaultMarks(marks.isEmpty() ? BigDecimal.ONE : parseDecimal(marks, "marks"));
        f.setText(get(r, "question"));
        f.setExplanation(get(r, "explanation"));
        f.setTags(get(r, "tags"));
        f.setCaseSensitive(yes(get(r, "case_sensitive")));
        f.setPartialMarking(yes(get(r, "partial")));
        String correct = get(r, "correct");
        List<String> opts = split(get(r, "options"), "\\|");
        switch (f.getType()) {
            case MCQ_SINGLE, MCQ_MULTI -> {
                Set<Integer> right = new HashSet<>();
                for (String p : split(correct, "[,;]")) {
                    try {
                        right.add(Integer.parseInt(p.trim()));
                    } catch (NumberFormatException e) {
                        throw AppException.bad("\'correct\' must be option numbers such as 2 or 1,3.");
                    }
                }
                for (int i = 0; i < opts.size(); i++) {
                    f.getOptions().add(new OptionForm(null, opts.get(i), null, right.contains(i + 1)));
                }
            }
            case TRUE_FALSE -> {
                if (!correct.equalsIgnoreCase("true") && !correct.equalsIgnoreCase("false")) {
                    throw AppException.bad("\'correct\' must be TRUE or FALSE for true/false questions.");
                }
                f.setTfAnswer(correct.equalsIgnoreCase("true"));
            }
            case FILL_BLANK, SHORT_ANSWER -> {
                List<String> answers = opts.isEmpty() ? split(correct, "\\|") : opts;
                answers.forEach(a -> f.getOptions().add(new OptionForm(null, a, null, true)));
            }
            case MATCHING -> {
                for (String o : opts) {
                    int eq = o.indexOf('=');
                    if (eq <= 0 || eq == o.length() - 1) {
                        throw AppException.bad("Matching pairs must look like left=right.");
                    }
                    f.getOptions().add(new OptionForm(null, o.substring(0, eq).trim(), o.substring(eq + 1).trim(), true));
                }
            }
            case NUMERIC -> {
                f.setNumericAnswer(parseDecimal(correct, "correct"));
                String tol = get(r, "tolerance");
                f.setNumericTolerance(tol.isEmpty() ? BigDecimal.ZERO : parseDecimal(tol, "tolerance"));
            }
            case ESSAY -> {
            }
        }
        List<String> errs = questionService.validate(f);
        if (f.getText().isBlank()) {
            errs.add(0, "Question text is empty.");
        }
        if (!errs.isEmpty()) {
            throw AppException.bad(String.join(" ", errs));
        }
        return f;
    }

    @Transactional(readOnly = true)
    public byte[] export(AuthUser user, Long subjectId) {
        Set<Long> allowed = access.teacherSubjectIds(user);
        if (subjectId != null && !allowed.contains(subjectId)) {
            subjectId = null;
        }
        List<Question> list = questions.findAll(QuestionSpecs.search(allowed, subjectId, null, null, null, null, null));
        try (
            SXSSFWorkbook wb = new SXSSFWorkbook(100);
            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Questions");
            Row h = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.size(); i++) {
                h.createCell(i).setCellValue(COLUMNS.get(i));
            }
            int r = 1;
            for (Question q : list) {
                Row row = sheet.createRow(r++);
                String[] v = exportRow(q);
                for (int i = 0; i < v.length; i++) {
                    row.createCell(i).setCellValue(safe(v[i]));
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw AppException.bad("Export failed.");
        }
    }

    private String[] exportRow(Question q) {
        String options = "";
        String correct = "";
        switch (q.getType()) {
            case MCQ_SINGLE, MCQ_MULTI -> {
                options = q.getOptions().stream().map(QuestionOption::getText).collect(Collectors.joining("|"));
                List<String> idx = new ArrayList<>();
                for (int i = 0; i < q.getOptions().size(); i++) {
                    if (q.getOptions().get(i).isCorrect()) {
                        idx.add(String.valueOf(i + 1));
                    }
                }
                correct = String.join(",", idx);
            }
            case TRUE_FALSE -> correct = String.valueOf(q.getOptions().stream().anyMatch(o -> o.isCorrect() && "True".equalsIgnoreCase(o.getText()))).toUpperCase(Locale.ROOT);
            case FILL_BLANK, SHORT_ANSWER -> options = q.getOptions().stream().map(QuestionOption::getText).collect(Collectors.joining("|"));
            case MATCHING -> options = q.getOptions().stream().map(o -> o.getText() + "=" + o.getMatchText()).collect(Collectors.joining("|"));
            case NUMERIC -> correct = q.getNumericAnswer() == null ? "" : q.getNumericAnswer().stripTrailingZeros().toPlainString();
            case ESSAY -> {
            }
        }
        return new String[] {q.getSubject().getCode(), q.getCategory() == null ? "" : q.getCategory().getName(), q.getType().name(), q.getDifficulty().name(), q.getDefaultMarks().toPlainString(), q.getText(), options, correct, q.getType() == QuestionType.NUMERIC ? q.getNumericTolerance().stripTrailingZeros().toPlainString() : "", q.isCaseSensitive() ? "yes" : "", q.isPartialMarking() ? "yes" : "", q.getExplanation() == null ? "" : q.getExplanation(), String.join(",", q.getTags())};
    }

    public String templateCsv(String subjectCode) {
        String s = subjectCode == null || subjectCode.isBlank() ? "SUBJECT_CODE" : subjectCode;
        return String.join(",", COLUMNS) + "\r\n" + s + ",Basics,MCQ_SINGLE,EASY,1,\"Which planet is known as the Red Planet?\",Venus|Mars|Jupiter|Saturn,2,,,,Mars looks red due to iron oxide.,space\r\n" + s + ",Basics,MCQ_MULTI,MEDIUM,2,\"Select the prime numbers\",2|4|5|9,\"1,3\",,,yes,,maths\r\n" + s + ",Basics,TRUE_FALSE,EASY,1,\"The Sun is a star.\",,TRUE,,,,,\r\n" + s + ",Basics,FILL_BLANK,EASY,1,\"The capital of France is ____.\",Paris,,,,,,\r\n" + s + ",Basics,SHORT_ANSWER,MEDIUM,3,\"Name any three primary colours.\",red|blue|yellow,,,,,,\r\n" + s + ",Basics,MATCHING,MEDIUM,3,\"Match country to capital\",India=Delhi|Japan=Tokyo|Italy=Rome,,,,,,\r\n" + s + ",Basics,NUMERIC,MEDIUM,2,\"Value of pi to 2 decimals?\",,3.14,0.01,,,,\r\n" + s + ",Basics,ESSAY,HARD,10,\"Discuss the causes of the French Revolution.\",,,,,,,\r\n";
    }

    /** Word version of the template: a short note, then ONE table (heading row + sample rows) with the same columns as the CSV template. */
    public byte[] templateDocx(String subjectCode) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : templateCsv(subjectCode).split("\r\n")) {
            List<String> cells = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (ch == '"') {
                    quoted = !quoted;
                } else if (ch == ',' && !quoted) {
                    cells.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(ch);
                }
            }
            cells.add(cur.toString());
            rows.add(cells);
        }
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.xwpf.usermodel.XWPFRun title = doc.createParagraph().createRun();
            title.setBold(true);
            title.setFontSize(14);
            title.setText("MindBridgeX Assess - Question import template");
            doc.createParagraph().createRun().setText("Keep the table below as the first table in the file and do not change the heading row. One row per question. Options are separated by |, matching pairs are written left=right. Delete the sample rows before uploading.");
            int cols = rows.get(0).size();
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(rows.size(), cols);
            for (int r = 0; r < rows.size(); r++) {
                for (int c = 0; c < cols; c++) {
                    String v = c < rows.get(r).size() ? rows.get(r).get(c) : "";
                    org.apache.poi.xwpf.usermodel.XWPFRun run = t.getRow(r).getCell(c).getParagraphs().get(0).createRun();
                    run.setBold(r == 0);
                    run.setText(v);
                }
            }
            doc.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String get(Map<String, String> r, String key) {
        String v = r.get(key);
        return v == null ? "" : v.trim();
    }

    private static List<String> split(String s, String regex) {
        if (s == null || s.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(s.split(regex)).map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toList());
    }

    private static boolean yes(String v) {
        return v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("y") || v.equals("1");
    }

    private static QuestionType parseType(String v) {
        String n = v.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (n) {
            case "MCQ", "SINGLE" -> QuestionType.MCQ_SINGLE;
            case "MULTI", "MULTIPLE" -> QuestionType.MCQ_MULTI;
            case "TF", "TRUEFALSE" -> QuestionType.TRUE_FALSE;
            case "FILL", "BLANK" -> QuestionType.FILL_BLANK;
            case "SHORT" -> QuestionType.SHORT_ANSWER;
            default -> parseEnum(QuestionType.class, n, "type");
        };
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> c, String v, String col) {
        try {
            return Enum.valueOf(c, v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw AppException.bad("Invalid " + col + " \'" + v + "\'.");
        }
    }

    private static BigDecimal parseDecimal(String v, String col) {
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            throw AppException.bad("\'" + col + "\' must be a number.");
        }
    }

    private static String safe(String v) {
        if (v == null) {
            return "";
        }
        return !v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 ? "\'" + v : v;
    }

    public QuestionImportExportService(final QuestionService questionService, final AccessService access, final QuestionCategoryRepository categories, final QuestionRepository questions, final AuditService audit) {
        this.questionService = questionService;
        this.access = access;
        this.categories = categories;
        this.questions = questions;
        this.audit = audit;
    }
}
