package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.exception.AppException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a question paper written as ordinary paragraphs instead of a table:
 * <pre>
 * Question 1. What is the output?      (or Q1. ...)
 * A. 10   B. 20 ...                    (one option per paragraph; A-F, or 1-6)
 * Correct Answer: B                    (letter, number or the text of the right option)
 * Marks: 2        Explanation: ...
 * </pre>
 * Pictures are attached to the question or to an option from where they stand in the document. A picture whose place is not clear
 * is never dropped: it is placed in the most likely slot and the row carries a warning so the teacher can check or move it.
 * The correct answer is only taken from an explicit answer line, never guessed.
 */
@Component
public class DocxFreeFormParser {
    private static final Pattern Q_PREFIXED = Pattern.compile("^(?:Q|Question)\\s*\\.?\\s*(\\d{1,4})\\s*[.):\\-]?\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern Q_PLAIN = Pattern.compile("^(\\d{1,4})\\s*[.)]\\s+(.+)$", Pattern.DOTALL);
    private static final Pattern OPT_LETTER = Pattern.compile("^\\(?([A-Fa-f])\\)?\\s*[.):\\-]\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern OPT_NUMBER = Pattern.compile("^\\(?([1-6])\\)?\\s*[.)]\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern ANSWER = Pattern.compile("^(?:Correct\\s*)?(?:Answer|Ans\\.?|Key)\\s*[:\\-]\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MARKS = Pattern.compile("^Marks?\\s*[:\\-]\\s*([0-9]+(?:\\.[0-9]+)?).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE = Pattern.compile("^Negative(?:\\s*marks?)?\\s*[:\\-].*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLANATION = Pattern.compile("^Explanation\\s*[:\\-]\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META = Pattern.compile("^(CO|Bloom'?s?|Difficulty|Unit|Category|Tags?)\\s*[:\\-]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PICTURE_ID = Pattern.compile("(?:r:embed|r:id|r:pict)=\"(rId[0-9]+)\"");
    private static final Pattern LETTERS = Pattern.compile("[A-F](\\s*[,;/&\\s]?\\s*[A-F])*");

    private final FileStorageService storage;

    public DocxFreeFormParser(FileStorageService storage) {
        this.storage = storage;
    }

    /** Parsed rows, or an empty list when the document has no recognisable questions. */
    public List<DocxQuestionParser.ParsedRow> parse(InputStream in) {
        try (XWPFDocument doc = new XWPFDocument(in)) {
            List<DocxQuestionParser.ParsedRow> rows = run(doc, false);
            return rows.isEmpty() ? run(doc, true) : rows;
        } catch (IOException | RuntimeException e) {
            if (e instanceof AppException ae) {
                throw ae;
            }
            throw AppException.bad("This is not a readable Word (.docx) file.");
        }
    }

    // ------------------------------------------------------------------ one pass over the paragraphs
    private static final class Draft {
        String qNo = "";
        final StringBuilder stem = new StringBuilder();
        String qImage;
        final List<StringBuilder> optText = new ArrayList<>();
        final List<String> optImage = new ArrayList<>();
        String answerRaw;
        String marks = "";
        String co = "";
        String bloom = "";
        String difficulty = "";
        String unit = "";
        String category = "";
        String tags = "";
        final StringBuilder explanation = new StringBuilder();
        final List<String> warnings = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        char last = 'Q';   // Q stem, O option, E explanation, X other
    }

    private List<DocxQuestionParser.ParsedRow> run(XWPFDocument doc, boolean plainNumbers) {
        List<DocxQuestionParser.ParsedRow> out = new ArrayList<>();
        Draft d = null;
        for (XWPFParagraph p : doc.getParagraphs()) {
            String text = p.getText() == null ? "" : p.getText().replace(' ', ' ').trim();
            List<String> pictures = pictures(doc, p, d);
            Matcher start = (plainNumbers ? Q_PLAIN : Q_PREFIXED).matcher(text);
            if (start.matches()) {
                if (d != null) {
                    out.add(finish(d, out.size() + 1));
                }
                d = new Draft();
                d.qNo = start.group(1);
                d.stem.append(start.group(2).trim());
                for (String pic : pictures) {
                    place(d, pic, 'Q', -1);
                }
                if (out.size() >= DocxQuestionParser.MAX_ROWS) {
                    throw AppException.bad("A file can hold at most " + DocxQuestionParser.MAX_ROWS + " questions. Split it into more files.");
                }
                continue;
            }
            if (d == null) {
                continue; // heading, instructions or anything before the first question
            }
            if (!text.isEmpty()) {
                Matcher m;
                if ((m = ANSWER.matcher(text)).matches()) {
                    d.answerRaw = m.group(1).trim();
                    d.last = 'X';
                } else if ((m = MARKS.matcher(text)).matches()) {
                    d.marks = m.group(1);
                    d.last = 'X';
                } else if (NEGATIVE.matcher(text).matches()) {
                    d.warnings.add("Negative marks written in the file are ignored: negative marking is set once for the whole exam.");
                    d.last = 'X';
                } else if ((m = EXPLANATION.matcher(text)).matches()) {
                    d.explanation.append(m.group(1).trim());
                    d.last = 'E';
                } else if ((m = META.matcher(text)).matches()) {
                    meta(d, m.group(1).toLowerCase(Locale.ROOT), m.group(2).trim());
                    d.last = 'X';
                } else if ((m = OPT_LETTER.matcher(text)).matches() && optionAllowed(d, m.group(1).toUpperCase(Locale.ROOT).charAt(0) - 'A')) {
                    addOption(d, m.group(1).toUpperCase(Locale.ROOT).charAt(0) - 'A', m.group(2).trim(), pictures);
                    continue;
                } else if (!plainNumbers && (m = OPT_NUMBER.matcher(text)).matches() && optionAllowed(d, Integer.parseInt(m.group(1)) - 1)) {
                    addOption(d, Integer.parseInt(m.group(1)) - 1, m.group(2).trim(), pictures);
                    continue;
                } else if (d.last == 'E') {
                    d.explanation.append(d.explanation.length() == 0 ? "" : "\n").append(text);
                } else if (d.optText.isEmpty()) {
                    d.stem.append(d.stem.length() == 0 ? "" : "\n").append(text);
                    d.last = 'Q';
                } else if (d.last == 'O') {
                    StringBuilder last = d.optText.get(d.optText.size() - 1);
                    last.append(last.length() == 0 ? "" : " ").append(text); // a wrapped option line
                }
            }
            for (String pic : pictures) {
                placeByContext(d, pic);
            }
        }
        if (d != null) {
            out.add(finish(d, out.size() + 1));
        }
        return out;
    }

    /** Options must follow each other: a new letter is accepted when it is the next one (or the first). */
    private static boolean optionAllowed(Draft d, int index) {
        return index == d.optText.size() && index < DocxQuestionParser.OPTION_LETTERS.length
                || (index > d.optText.size() && index < DocxQuestionParser.OPTION_LETTERS.length && !d.optText.isEmpty());
    }

    private void addOption(Draft d, int index, String text, List<String> pictures) {
        while (d.optText.size() < index) {
            d.optText.add(new StringBuilder());
            d.optImage.add(null);
            d.warnings.add("Option " + DocxQuestionParser.OPTION_LETTERS[d.optText.size() - 1] + " is missing between the options found.");
        }
        d.optText.add(new StringBuilder(text));
        d.optImage.add(null);
        d.last = 'O';
        for (String pic : pictures) {
            place(d, pic, 'O', index);
        }
    }

    private static void meta(Draft d, String key, String value) {
        switch (key) {
            case "co" -> d.co = value;
            case "difficulty" -> d.difficulty = value;
            case "unit" -> d.unit = value;
            case "category" -> d.category = value;
            case "tag", "tags" -> d.tags = value;
            default -> d.bloom = value; // bloom, bloom's, blooms
        }
    }

    // ------------------------------------------------------------------ pictures
    /** Stores the pictures of a paragraph in document order; a picture that cannot be read is reported on the draft instead of being lost silently. */
    private List<String> pictures(XWPFDocument doc, XWPFParagraph p, Draft d) {
        List<String> names = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        Matcher m = PICTURE_ID.matcher(p.getCTP().xmlText());
        while (m.find()) {
            ids.add(m.group(1));
        }
        for (String id : ids) {
            XWPFPictureData pd = doc.getPictureDataByID(id);
            if (pd == null) {
                continue;
            }
            String name = storage.storeImageBytes(pd.getData());
            if (name == null) {
                if (d != null) {
                    d.problems.add("A picture in this question could not be read (use PNG, JPG, GIF, WebP or a picture within the size and dimension limit).");
                }
            } else {
                names.add(name);
            }
        }
        return names;
    }

    /** A picture standing on its own line: question picture before the options, otherwise the option just above it. */
    private void placeByContext(Draft d, String pic) {
        if (d.optText.isEmpty()) {
            place(d, pic, 'Q', -1);
            return;
        }
        if (d.last == 'O') {
            place(d, pic, 'O', d.optText.size() - 1);
            return;
        }
        place(d, pic, '?', -1);
    }

    private void place(Draft d, String pic, char target, int optionIndex) {
        if (target == 'Q' && d.qImage == null) {
            d.qImage = pic;
            return;
        }
        if (target == 'O' && optionIndex >= 0 && optionIndex < d.optImage.size() && d.optImage.get(optionIndex) == null) {
            d.optImage.set(optionIndex, pic);
            if (d.optText.get(optionIndex).length() > 0) {
                d.warnings.add("A picture was added to option " + DocxQuestionParser.OPTION_LETTERS[optionIndex] + ", which also has text. Check it.");
            }
            return;
        }
        // a second picture before any option: most likely the options themselves, one picture each
        if (target == 'Q' && d.qImage != null && d.optText.size() < DocxQuestionParser.OPTION_LETTERS.length) {
            d.optText.add(new StringBuilder());
            d.optImage.add(pic);
            d.warnings.add("Image detected, but its question/option association needs review: it was taken as option " + DocxQuestionParser.OPTION_LETTERS[d.optText.size() - 1] + ".");
            d.last = 'O';
            return;
        }
        // not clear where it belongs: use the first free slot and ask the teacher to check
        if (d.qImage == null) {
            d.qImage = pic;
            d.warnings.add("Image detected, but its question/option association needs review: it was placed on the question.");
            return;
        }
        for (int i = 0; i < d.optImage.size(); i++) {
            if (d.optImage.get(i) == null) {
                d.optImage.set(i, pic);
                d.warnings.add("Image detected, but its question/option association needs review: it was placed on option " + DocxQuestionParser.OPTION_LETTERS[i] + ".");
                return;
            }
        }
        d.warnings.add("An extra picture could not be placed on this question or any option and was left out.");
        storage.deleteQuietly(pic);
    }

    // ------------------------------------------------------------------ finishing a question
    private DocxQuestionParser.ParsedRow finish(Draft d, int rowNo) {
        List<DocxQuestionParser.Cell> options = new ArrayList<>();
        for (int i = 0; i < d.optText.size(); i++) {
            options.add(new DocxQuestionParser.Cell(d.optText.get(i).toString().trim(), d.optImage.get(i), null));
        }
        String correct = "";
        if (d.answerRaw != null && !d.answerRaw.isBlank()) {
            correct = resolve(d.answerRaw, options);
            if (correct.isEmpty()) {
                d.warnings.add("The answer \"" + shorten(d.answerRaw) + "\" does not match an option. Choose the correct option.");
            }
        } else {
            d.warnings.add("No answer line was found for this question. Choose the correct option.");
        }
        if (options.size() > DocxQuestionParser.OPTION_LETTERS.length) {
            d.problems.add("A question can have at most " + DocxQuestionParser.OPTION_LETTERS.length + " options.");
        }
        String problem = d.problems.isEmpty() ? null : String.join(" ", d.problems);
        String warning = d.warnings.isEmpty() ? null : String.join(" ", d.warnings);
        DocxQuestionParser.Cell question = new DocxQuestionParser.Cell(d.stem.toString().trim(), d.qImage, problem);
        return new DocxQuestionParser.ParsedRow(rowNo, d.qNo, question, options, correct, d.marks, d.co, d.difficulty, d.bloom, d.unit, warning,
                d.explanation.toString().trim(), d.category, d.tags);
    }

    /** "B", "A, C", "Option B", "2" or the text of the right option become letters; anything else stays unresolved (never guessed). */
    static String resolve(String raw, List<DocxQuestionParser.Cell> options) {
        String c = raw.toUpperCase(Locale.ROOT).replace("OPTION", "").replace("(", "").replace(")", "").replace(".", "").trim();
        if (LETTERS.matcher(c).matches()) {
            return joined(c.chars().filter(ch -> ch >= 'A' && ch <= 'F').mapToObj(ch -> String.valueOf((char) ch)).distinct().toList(), options.size());
        }
        if (c.matches("[1-6]([,;/&\\s]+[1-6])*")) {
            List<String> letters = new ArrayList<>();
            for (String n : c.split("[,;/&\\s]+")) {
                String l = DocxQuestionParser.OPTION_LETTERS[Integer.parseInt(n) - 1];
                if (!letters.contains(l)) {
                    letters.add(l);
                }
            }
            return joined(letters, options.size());
        }
        String want = normalise(raw);
        for (int i = 0; i < options.size(); i++) {
            if (!want.isEmpty() && want.equals(normalise(options.get(i).text()))) {
                return DocxQuestionParser.OPTION_LETTERS[i];
            }
        }
        return "";
    }

    private static String joined(List<String> letters, int optionCount) {
        for (String l : letters) {
            if (l.charAt(0) - 'A' >= optionCount) {
                return String.join(",", letters); // points past the options: the validator will say so
            }
        }
        return String.join(",", letters);
    }

    private static String normalise(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String shorten(String s) {
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }
}
