package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.exception.AppException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the standard question-paper template: one table, one row per question.
 * Columns: Q No | Question | Option A | Option B | Option C | Option D | Correct | Marks | CO | Difficulty.
 * Every question/option cell may hold text, a picture, or both. Pictures are stored right away.
 */
@Component
public class DocxQuestionParser {
    public static final int MAX_ROWS = 300;
    public static final String[] OPTION_LETTERS = {"A", "B", "C", "D", "E", "F"};

    private final FileStorageService storage;

    /** One cell (or one question / option of a free-form document). "problem" blocks the row; "warning" only asks the teacher to look. */
    public record Cell(String text, String image, String problem, String warning) {
        public Cell(String text, String image, String problem) {
            this(text, image, problem, null);
        }
    }

    public record ParsedRow(int rowNo, String qNo, Cell question, List<Cell> options, String correct, String marks, String co, String difficulty, String bloom, String unit,
                            String warning, String explanation, String category, String tags) {
        public ParsedRow(int rowNo, String qNo, Cell question, List<Cell> options, String correct, String marks, String co, String difficulty, String bloom, String unit) {
            this(rowNo, qNo, question, options, correct, marks, co, difficulty, bloom, unit, null, null, null, null);
        }
    }

    public DocxQuestionParser(FileStorageService storage) {
        this.storage = storage;
    }

    public List<ParsedRow> parse(InputStream in) {
        try (XWPFDocument doc = new XWPFDocument(in)) {
            XWPFTable table = null;
            int headerIndex = -1;
            java.util.Map<String, Integer> cols = new java.util.HashMap<>();
            outer:
            for (XWPFTable t : doc.getTables()) {
                for (int r = 0; r < Math.min(3, t.getNumberOfRows()); r++) {
                    java.util.Map<String, Integer> m = headerMap(t.getRow(r));
                    if (m.containsKey("question") && m.containsKey("optiona") && m.containsKey("optionb") && m.containsKey("correct")) {
                        table = t;
                        headerIndex = r;
                        cols = m;
                        break outer;
                    }
                }
            }
            if (table == null) {
                throw AppException.bad("No question table found. Please use the standard template: a table whose heading row has Question, Option A, Option B, Correct.");
            }
            List<ParsedRow> rows = new ArrayList<>();
            for (int r = headerIndex + 1; r < table.getNumberOfRows(); r++) {
                if (rows.size() >= MAX_ROWS) {
                    throw AppException.bad("A file can hold at most " + MAX_ROWS + " questions. Split it into more files.");
                }
                XWPFTableRow row = table.getRow(r);
                Cell q = cell(doc, row, cols.get("question"), "Question");
                List<Cell> opts = new ArrayList<>();
                boolean any = !q.text().isEmpty() || q.image() != null || q.problem() != null;
                for (int i = 0; i < OPTION_LETTERS.length; i++) {
                    Integer c = cols.get("option" + OPTION_LETTERS[i].toLowerCase(Locale.ROOT));
                    Cell o = c == null ? new Cell("", null, null) : cell(doc, row, c, "Option " + OPTION_LETTERS[i]);
                    opts.add(o);
                    any |= !o.text().isEmpty() || o.image() != null || o.problem() != null;
                }
                String correct = text(row, cols.get("correct"));
                any |= !correct.isEmpty();
                if (!any) {
                    continue;
                }
                rows.add(new ParsedRow(r + 1, text(row, cols.get("qno")), q, opts, correct, text(row, cols.get("marks")), text(row, cols.get("co")), text(row, cols.get("difficulty")), text(row, cols.get("bloom")), text(row, cols.get("unit"))));
            }
            if (rows.isEmpty()) {
                throw AppException.bad("The table has no question rows.");
            }
            return rows;
        } catch (IOException | RuntimeException e) {
            if (e instanceof AppException ae) {
                throw ae;
            }
            throw AppException.bad("This is not a readable Word (.docx) file. Please use the standard template.");
        }
    }

    private static java.util.Map<String, Integer> headerMap(XWPFTableRow row) {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        List<XWPFTableCell> cells = row.getTableCells();
        for (int i = 0; i < cells.size(); i++) {
            String h = cells.get(i).getText().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            String key = switch (h) {
                case "qno", "q", "no", "sno", "srno", "slno", "questionno", "qnumber" -> "qno";
                case "question", "questiontextimage", "questiontext" -> "question";
                case "optiona", "a" -> "optiona";
                case "optionb", "b" -> "optionb";
                case "optionc", "c" -> "optionc";
                case "optiond", "d" -> "optiond";
                case "optione", "e" -> "optione";
                case "optionf", "f" -> "optionf";
                case "correct", "correctanswer", "answer", "correctoption", "key" -> "correct";
                case "marks", "mark" -> "marks";
                case "co", "courseoutcome", "coid" -> "co";
                case "difficulty", "difficultylevel" -> "difficulty";
                case "bloom", "blooms", "bloomlevel", "bloomslevel", "bloomstaxonomy", "level" -> "bloom";
                case "unit", "module", "unitmodule" -> "unit";
                default -> null;
            };
            if (key != null) {
                m.putIfAbsent(key, i);
            }
        }
        return m;
    }

    /** Word equations are not part of the plain text; their characters are added in a readable linear form, e.g. "V = I R". */
    private static String equations(XWPFTableCell c) {
        String xml = c.getCTTc().xmlText();
        if (!xml.contains("oMath")) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        java.util.regex.Matcher blocks = java.util.regex.Pattern.compile("<m:oMath>(.*?)</m:oMath>", java.util.regex.Pattern.DOTALL).matcher(xml);
        while (blocks.find()) {
            StringBuilder eq = new StringBuilder();
            java.util.regex.Matcher t = java.util.regex.Pattern.compile("<m:t[^>]*>([^<]*)</m:t>").matcher(blocks.group(1));
            while (t.find()) {
                eq.append(t.group(1));
            }
            if (eq.length() > 0) {
                out.append(' ').append(eq);
            }
        }
        return out.toString();
    }

    private String text(XWPFTableRow row, Integer col) {
        if (col == null || col >= row.getTableCells().size()) {
            return "";
        }
        return row.getCell(col).getText().replace(' ', ' ').trim();
    }

    private Cell cell(XWPFDocument doc, XWPFTableRow row, Integer col, String label) {
        if (col == null || col >= row.getTableCells().size()) {
            return new Cell("", null, null);
        }
        XWPFTableCell c = row.getCell(col);
        String text = (c.getText() + equations(c)).replace(' ', ' ').trim();
        String image = null;
        String problem = null;
        String warning = null;
        java.util.Set<String> pictureIds = new java.util.LinkedHashSet<>();
        // Find every picture in the cell however it was inserted (in line, floating, wrapped, or older Word format).
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:r:embed|r:id|r:pict)=\"(rId[0-9]+)\"").matcher(c.getCTTc().xmlText());
        while (m.find() && image == null && problem == null) {
            org.apache.poi.xwpf.usermodel.XWPFPictureData pd = doc.getPictureDataByID(m.group(1));
            if (pd == null) {
                continue;
            }
            image = storage.storeImageBytes(pd.getData());
            if (image == null) {
                problem = label + ": the picture could not be read (use PNG, JPG, GIF, WebP or a picture within the size and dimension limit).";
            }
        }
        java.util.regex.Matcher all = java.util.regex.Pattern.compile("(?:r:embed|r:id|r:pict)=\"(rId[0-9]+)\"").matcher(c.getCTTc().xmlText());
        while (all.find()) {
            if (doc.getPictureDataByID(all.group(1)) != null) {
                pictureIds.add(all.group(1));
            }
        }
        if (pictureIds.size() > 1 && problem == null) {
            warning = label + ": " + pictureIds.size() + " pictures were found in this cell; only the first one is used. Check the others.";
        }
        return new Cell(text, image, problem, warning);
    }

    // ------------------------------------------------------------------ template
    /** The college question-paper template (NPTEL ESE format): header table, instructions, the question table (CO 1-10, Bloom's 1-6) and chart placeholders. */
    public byte[] template() {
        try (InputStream in = DocxQuestionParser.class.getResourceAsStream("/docx/question-paper-template.docx")) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            // fall back to the generated template below
        }
        return basicTemplate();
    }

    /** Fallback template with instructions and three sample rows (used only if the bundled template file is missing). */
    private byte[] basicTemplate() {
        try (XWPFDocument doc = new XWPFDocument(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            XWPFRun title = doc.createParagraph().createRun();
            title.setBold(true);
            title.setFontSize(16);
            title.setText("MindBridgeX Assess - Question paper template (MCQ)");
            String[] notes = {
                    "Fill one row per question. Do not rename or reorder the heading row. Keep the table; do not add other tables.",
                    "Question and Option cells can hold text, a picture, or both. Insert pictures with Insert > Picture, any layout is fine.",
                    "Correct: the letter of the right option, for example B. For a multiple-answer question write several letters, for example A,C.",
                    "CO is the Course Outcome code of the subject, for example CO2, and is required once the subject has Course Outcomes. Bloom's is Remember, Understand, Apply, Analyze, Evaluate or Create. Marks (default 1), Difficulty (Easy, Medium, Hard) and Unit are optional.",
                    "Equations: use Insert > Equation in Word. Code: paste it in the question cell (line breaks are kept). Pictures: Insert > Picture, any layout.",
                    "Each option may be left empty only at the end (C and D can be left out for a 2-option question). At least two options are required.",
                    "Delete the three sample rows before you send the file.",
            };
            for (String n : notes) {
                doc.createParagraph().createRun().setText("- " + n);
            }
            String[] head = {"Q No", "Question", "Option A", "Option B", "Option C", "Option D", "Correct", "Marks", "CO", "Bloom's", "Difficulty", "Unit"};
            XWPFTable t = doc.createTable(4, head.length);
            for (int i = 0; i < head.length; i++) {
                XWPFRun r = t.getRow(0).getCell(i).getParagraphs().get(0).createRun();
                r.setBold(true);
                r.setText(head[i]);
            }
            String[][] sample = {
                    {"1", "What is the capital of India?", "Mumbai", "New Delhi", "Kolkata", "Chennai", "B", "1", "CO1", "Remember", "Easy", "Unit 1"},
                    {"2", "Which of these are prime numbers?", "2", "4", "5", "9", "A,C", "2", "CO1", "Apply", "Medium", "Unit 1"},
                    {"3", "(Picture question) Identify the circuit shown. Put the picture in this cell.", "Series", "Parallel", "Bridge", "Mixed", "A", "1", "CO2", "Analyze", "Hard", "Unit 2"},
            };
            for (int r = 0; r < sample.length; r++) {
                for (int c = 0; c < head.length; c++) {
                    t.getRow(r + 1).getCell(c).setText(sample[r][c]);
                }
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
