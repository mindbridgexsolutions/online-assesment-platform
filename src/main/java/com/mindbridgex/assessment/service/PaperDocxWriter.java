package com.mindbridgex.assessment.service;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Writes a question paper as a Word file in the standard upload template, so it can be edited in Word and uploaded back.
 * Pictures of questions and options are embedded in their cells (DocxQuestionParser reads them again on the way back in).
 */
@Component
public class PaperDocxWriter {
    private static final int MAX_PICTURE_WIDTH_PX = 260;
    private final FileStorageService storage;

    public PaperDocxWriter(FileStorageService storage) {
        this.storage = storage;
    }

    public byte[] write(ExamUploadService.Session paper) {
        int optionColumns = 4;
        for (ExamUploadService.Row r : paper.rows()) {
            for (int i = r.item().options().size() - 1; i >= optionColumns; i--) {
                ExamUploadService.Opt o = r.item().options().get(i);
                if ((o.text() != null && !o.text().isBlank()) || o.image() != null) {
                    optionColumns = Math.max(optionColumns, i + 1);
                    break;
                }
            }
        }
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFRun title = doc.createParagraph().createRun();
            title.setBold(true);
            title.setFontSize(16);
            title.setText("Question paper: " + paper.fileName());
            doc.createParagraph().createRun().setText("Subject: " + (paper.subjectName() == null ? "" : paper.subjectName()) + ". " + paper.rows().size() + " row(s).");
            doc.createParagraph().createRun().setText("- Edit the table below in Word: change text, pictures, the Correct letter, Marks, CO, Bloom's, Difficulty and Unit; add or delete rows. "
                    + "Keep the heading row and do not add other tables.");
            doc.createParagraph().createRun().setText("- Then open this paper in MindBridgeX Assess and use \"Replace from Word\". The paper is replaced by what is in this table.");
            doc.createParagraph().createRun().setText("- Correct: the letter of the right option, for example B, or A,C for several. Equations and pictures can be inserted in any cell.");

            String[] fixedHead = {"Q No", "Question"};
            String[] tailHead = {"Correct", "Marks", "CO", "Bloom's", "Difficulty", "Unit"};
            int cols = fixedHead.length + optionColumns + tailHead.length;
            XWPFTable t = doc.createTable(paper.rows().size() + 1, cols);
            int c = 0;
            for (String h : fixedHead) {
                header(t.getRow(0).getCell(c++), h);
            }
            for (int i = 0; i < optionColumns; i++) {
                header(t.getRow(0).getCell(c++), "Option " + DocxQuestionParser.OPTION_LETTERS[i]);
            }
            for (String h : tailHead) {
                header(t.getRow(0).getCell(c++), h);
            }
            int rowIdx = 1;
            for (ExamUploadService.Row r : paper.rows()) {
                ExamUploadService.Item it = r.item();
                int col = 0;
                put(doc, t.getRow(rowIdx).getCell(col++), it.qNo() == null || it.qNo().isBlank() ? String.valueOf(rowIdx) : it.qNo(), null);
                put(doc, t.getRow(rowIdx).getCell(col++), it.text(), it.image());
                for (int i = 0; i < optionColumns; i++) {
                    ExamUploadService.Opt o = i < it.options().size() ? it.options().get(i) : null;
                    put(doc, t.getRow(rowIdx).getCell(col++), o == null ? "" : o.text(), o == null ? null : o.image());
                }
                put(doc, t.getRow(rowIdx).getCell(col++), it.correct(), null);
                put(doc, t.getRow(rowIdx).getCell(col++), it.marks(), null);
                put(doc, t.getRow(rowIdx).getCell(col++), it.co(), null);
                put(doc, t.getRow(rowIdx).getCell(col++), it.bloom(), null);
                put(doc, t.getRow(rowIdx).getCell(col++), it.difficulty(), null);
                put(doc, t.getRow(rowIdx).getCell(col), it.unit(), null);
                rowIdx++;
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void header(XWPFTableCell cell, String text) {
        XWPFRun r = cell.getParagraphs().get(0).createRun();
        r.setBold(true);
        r.setText(text);
    }

    private void put(XWPFDocument doc, XWPFTableCell cell, String text, String image) {
        XWPFParagraph p = cell.getParagraphs().get(0);
        if (text != null && !text.isEmpty()) {
            String[] lines = text.split("\n", -1);
            XWPFRun run = p.createRun();
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    run.addBreak();
                }
                run.setText(lines[i]);
            }
        }
        if (image != null) {
            addPicture(cell, image);
        }
    }

    private void addPicture(XWPFTableCell cell, String name) {
        try {
            Resource res = storage.load(name);
            byte[] data;
            try (InputStream in = res.getInputStream()) {
                data = in.readAllBytes();
            }
            int type = pictureType(name);
            if (type < 0) {
                return;
            }
            int w = 200;
            int h = 150;
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img != null) {
                w = Math.min(img.getWidth(), MAX_PICTURE_WIDTH_PX);
                h = Math.max(1, (int) Math.round(img.getHeight() * (w / (double) img.getWidth())));
            }
            XWPFParagraph p = cell.addParagraph();
            p.createRun().addPicture(new ByteArrayInputStream(data), type, name, Units.toEMU(w), Units.toEMU(h));
        } catch (Exception ignored) {
            // a missing or unreadable picture is left out of the Word file rather than failing the whole download
        }
    }

    private static int pictureType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) {
            return Document.PICTURE_TYPE_PNG;
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return Document.PICTURE_TYPE_JPEG;
        }
        if (n.endsWith(".gif")) {
            return Document.PICTURE_TYPE_GIF;
        }
        return -1;
    }
}
