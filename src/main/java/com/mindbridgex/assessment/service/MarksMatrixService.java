package com.mindbridgex.assessment.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mindbridgex.assessment.entity.Exam;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Student-wise, question-wise marks sheet for one exam (NBA / OBE style): every student's marks on every question,
 * with the Course Outcome, Bloom's level and unit of each question, CO-wise totals per student, and the
 * question-wise and CO-wise attainment summary. Uses each student's best finished attempt.
 */
@Service
public class MarksMatrixService {

    public record Col(String key, String label, String text, String co, String bloom, String unit, BigDecimal max) {
    }

    public record Stu(Long attemptId, String name, String roll, Map<String, BigDecimal> marks, java.util.Set<String> pending, BigDecimal total, BigDecimal max, BigDecimal percent, Map<String, BigDecimal> coPercent) {
    }

    public record QStat(int students, BigDecimal average, BigDecimal attainedPercent) {
    }

    public record CoStat(String code, String description, BigDecimal target, int students, int attained, BigDecimal attainedPercent, int level) {
    }

    public record Matrix(Exam exam, List<Col> cols, List<Stu> students, List<String> coCodes, Map<String, QStat> qStats, List<CoStat> coStats) {
    }

    private static final BigDecimal DEFAULT_TARGET = BigDecimal.valueOf(60);

    private final JdbcClient jdbc;
    private final ReportService reports;

    public MarksMatrixService(JdbcClient jdbc, ReportService reports) {
        this.jdbc = jdbc;
        this.reports = reports;
    }

    @Transactional(readOnly = true)
    public Matrix build(AuthUser user, Long examId) {
        Exam exam = reports.examFor(user, examId);

        Map<String, BigDecimal> targets = new HashMap<>();
        Map<String, String> coDesc = new LinkedHashMap<>();
        jdbc.sql("select code, description, target_percent from course_outcomes where subject_id = :s order by code").param("s", exam.getSubject().getId()).query((rs, n) -> {
            targets.put(rs.getString(1), rs.getBigDecimal(3));
            coDesc.put(rs.getString(1), rs.getString(2));
            return 0;
        }).list();

        record Best(Long attemptId, Long studentId, String name, String roll, BigDecimal total, BigDecimal max, BigDecimal pct) {
        }
        List<Best> best = jdbc.sql("""
                with b as (
                  select distinct on (a.student_id) a.id attempt_id, a.student_id, g.total_score, g.max_score, g.percentage
                  from attempts a join grades g on g.attempt_id = a.id
                  where a.exam_id = :e and a.status <> 'IN_PROGRESS'
                  order by a.student_id, g.percentage desc, a.id desc)
                select b.attempt_id, b.student_id, u.full_name, u.roll_no, b.total_score, b.max_score, b.percentage
                from b join users u on u.id = b.student_id
                order by u.roll_no nulls last, u.full_name
                """).param("e", examId).query((rs, n) -> new Best(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7))).list();

        // questions and per-student marks
        Map<String, Object[]> colInfo = new LinkedHashMap<>(); // key -> {minPos, text, co, bloom, unit, max}
        Map<Long, Map<String, BigDecimal>> marks = new HashMap<>();
        Map<Long, java.util.Set<String>> pending = new HashMap<>();
        if (!best.isEmpty()) {
            jdbc.sql("""
                    select aq.attempt_id, coalesce(cast(aq.source_question_id as text), 'x' || aq.id) k, aq.position, aq.text, aq.marks,
                           co.code, q.bloom, q.unit, ans.awarded_marks, coalesce(ans.needs_manual, false), aq.void_mode
                    from attempt_questions aq
                    left join questions q on q.id = aq.source_question_id
                    left join course_outcomes co on co.id = coalesce(aq.co_id, q.co_id)
                    left join attempt_answers ans on ans.attempt_question_id = aq.id
                    where aq.attempt_id in (:ids)
                    order by aq.position
                    """).param("ids", best.stream().map(Best::attemptId).toList()).query((rs, n) -> {
                long attempt = rs.getLong(1);
                String key = rs.getString(2);
                int pos = rs.getInt(3);
                if (!"DROPPED".equals(rs.getString(11))) {
                    Object[] ci = colInfo.get(key);
                    if (ci == null) {
                        colInfo.put(key, new Object[] {pos, rs.getString(4), rs.getString(6), rs.getString(7), rs.getString(8), rs.getBigDecimal(5)});
                    } else if (pos < (Integer) ci[0]) {
                        ci[0] = pos;
                    }
                    BigDecimal awarded = rs.getBigDecimal(9);
                    if (rs.getBoolean(10) && awarded == null) {
                        pending.computeIfAbsent(attempt, k -> new java.util.HashSet<>()).add(key);
                    } else {
                        marks.computeIfAbsent(attempt, k -> new HashMap<>()).put(key, awarded == null ? BigDecimal.ZERO : awarded);
                    }
                }
                return 0;
            }).list();
        }
        List<Map.Entry<String, Object[]>> ordered = new ArrayList<>(colInfo.entrySet());
        ordered.sort((x, y) -> Integer.compare((Integer) x.getValue()[0], (Integer) y.getValue()[0]));
        List<Col> cols = new ArrayList<>();
        int qn = 1;
        for (Map.Entry<String, Object[]> en : ordered) {
            Object[] v = en.getValue();
            cols.add(new Col(en.getKey(), "Q" + qn++, (String) v[1], (String) v[2], bloomLabel((String) v[3]), (String) v[4], (BigDecimal) v[5]));
        }
        List<String> coCodes = new ArrayList<>();
        for (Col c : cols) {
            if (c.co() != null && !coCodes.contains(c.co())) {
                coCodes.add(c.co());
            }
        }
        coCodes.sort(String::compareTo);

        List<Stu> students = new ArrayList<>();
        for (Best b : best) {
            Map<String, BigDecimal> m = marks.getOrDefault(b.attemptId(), Map.of());
            Map<String, BigDecimal> coPct = new LinkedHashMap<>();
            for (String code : coCodes) {
                BigDecimal got = BigDecimal.ZERO;
                BigDecimal mx = BigDecimal.ZERO;
                for (Col c : cols) {
                    if (code.equals(c.co()) && m.containsKey(c.key())) {
                        got = got.add(m.get(c.key()));
                        mx = mx.add(c.max());
                    }
                }
                if (mx.signum() > 0) {
                    coPct.put(code, pct(got, mx));
                }
            }
            students.add(new Stu(b.attemptId(), b.name(), b.roll(), m, pending.getOrDefault(b.attemptId(), java.util.Set.of()), b.total(), b.max(), b.pct(), coPct));
        }

        Map<String, QStat> qStats = new LinkedHashMap<>();
        for (Col c : cols) {
            int n = 0;
            int ok = 0;
            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal target = c.co() == null ? DEFAULT_TARGET : targets.getOrDefault(c.co(), DEFAULT_TARGET);
            for (Stu s : students) {
                BigDecimal v = s.marks().get(c.key());
                if (v == null) {
                    continue;
                }
                n++;
                sum = sum.add(v);
                if (c.max().signum() > 0 && pct(v, c.max()).compareTo(target) >= 0) {
                    ok++;
                }
            }
            qStats.put(c.key(), new QStat(n, n == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP),
                    n == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(ok * 100.0 / n).setScale(1, RoundingMode.HALF_UP)));
        }
        List<CoStat> coStats = new ArrayList<>();
        for (String code : coCodes) {
            BigDecimal target = targets.getOrDefault(code, DEFAULT_TARGET);
            int n = 0;
            int ok = 0;
            for (Stu s : students) {
                BigDecimal p = s.coPercent().get(code);
                if (p == null) {
                    continue;
                }
                n++;
                if (p.compareTo(target) >= 0) {
                    ok++;
                }
            }
            BigDecimal att = n == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(ok * 100.0 / n).setScale(1, RoundingMode.HALF_UP);
            coStats.add(new CoStat(code, coDesc.getOrDefault(code, ""), target, n, ok, att, n == 0 ? 0 : OutcomeReportService.levelFor(att)));
        }
        return new Matrix(exam, cols, students, coCodes, qStats, coStats);
    }

    // ------------------------------------------------------------------ Excel
    @Transactional(readOnly = true)
    public byte[] excel(AuthUser user, Long examId) {
        Matrix m = build(user, examId);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            head.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            CellStyle boldOnly = wb.createCellStyle();
            boldOnly.setFont(bold);

            Sheet s = wb.createSheet("Marks matrix");
            int r = 0;
            put(s, r++, 0, "Exam: " + m.exam().getTitle(), boldOnly);
            put(s, r++, 0, "Subject: " + m.exam().getSubject().getName() + "   |   Students: " + m.students().size() + "   |   Best finished attempt per student", null);
            r++;
            int first = 3; // Sl, Roll no, Name
            String[] meta = {"Question", "CO", "Bloom's level", "Unit", "Max marks"};
            int[] metaRows = new int[meta.length];
            for (int i = 0; i < meta.length; i++) {
                metaRows[i] = r;
                Row row = s.createRow(r++);
                Cell c = row.createCell(first - 1);
                c.setCellValue(meta[i]);
                c.setCellStyle(head);
                for (int q = 0; q < m.cols().size(); q++) {
                    Col col = m.cols().get(q);
                    String v = switch (i) {
                        case 0 -> col.label();
                        case 1 -> nz(col.co());
                        case 2 -> nz(col.bloom());
                        case 3 -> nz(col.unit());
                        default -> col.max().stripTrailingZeros().toPlainString();
                    };
                    Cell cc = row.createCell(first + q);
                    if (i == 4) {
                        cc.setCellValue(col.max().doubleValue());
                    } else {
                        cc.setCellValue(safe(v));
                    }
                    cc.setCellStyle(head);
                }
            }
            int totalCol = first + m.cols().size();
            Row hdr = s.createRow(r++);
            String[] idHead = {"Sl", "Roll no", "Student name"};
            for (int i = 0; i < idHead.length; i++) {
                Cell c = hdr.createCell(i);
                c.setCellValue(idHead[i]);
                c.setCellStyle(head);
            }
            for (int q = 0; q < m.cols().size(); q++) {
                Cell c = hdr.createCell(first + q);
                c.setCellValue(m.cols().get(q).label());
                c.setCellStyle(head);
            }
            String[] tail = {"Total", "Max", "%"};
            for (int i = 0; i < tail.length; i++) {
                Cell c = hdr.createCell(totalCol + i);
                c.setCellValue(tail[i]);
                c.setCellStyle(head);
            }
            for (int i = 0; i < m.coCodes().size(); i++) {
                Cell c = hdr.createCell(totalCol + 3 + i);
                c.setCellValue(m.coCodes().get(i) + " %");
                c.setCellStyle(head);
            }
            int sl = 1;
            for (Stu st : m.students()) {
                Row row = s.createRow(r++);
                row.createCell(0).setCellValue(sl++);
                row.createCell(1).setCellValue(safe(nz(st.roll())));
                row.createCell(2).setCellValue(safe(nz(st.name())));
                for (int q = 0; q < m.cols().size(); q++) {
                    String key = m.cols().get(q).key();
                    BigDecimal v = st.marks().get(key);
                    if (v != null) {
                        row.createCell(first + q).setCellValue(v.doubleValue());
                    } else if (st.pending().contains(key)) {
                        row.createCell(first + q).setCellValue("Pending");
                    }
                }
                row.createCell(totalCol).setCellValue(st.total().doubleValue());
                row.createCell(totalCol + 1).setCellValue(st.max().doubleValue());
                row.createCell(totalCol + 2).setCellValue(st.percent().doubleValue());
                for (int i = 0; i < m.coCodes().size(); i++) {
                    BigDecimal p = st.coPercent().get(m.coCodes().get(i));
                    if (p != null) {
                        row.createCell(totalCol + 3 + i).setCellValue(p.doubleValue());
                    }
                }
            }
            r++;
            String[] foot = {"Students who answered", "Average marks", "% students at or above CO target"};
            for (int i = 0; i < foot.length; i++) {
                Row row = s.createRow(r++);
                Cell c = row.createCell(first - 1);
                c.setCellValue(foot[i]);
                c.setCellStyle(boldOnly);
                for (int q = 0; q < m.cols().size(); q++) {
                    QStat qs = m.qStats().get(m.cols().get(q).key());
                    double v = i == 0 ? qs.students() : i == 1 ? qs.average().doubleValue() : qs.attainedPercent().doubleValue();
                    row.createCell(first + q).setCellValue(v);
                }
            }
            s.createFreezePane(first, metaRows[metaRows.length - 1] + 2);
            s.setColumnWidth(0, 6 * 256);
            s.setColumnWidth(1, 14 * 256);
            s.setColumnWidth(2, 28 * 256);
            for (int q = 0; q < m.cols().size(); q++) {
                s.setColumnWidth(first + q, 9 * 256);
            }

            Sheet co = wb.createSheet("CO attainment");
            String[] ch = {"CO", "Description", "Target %", "Students", "Attained target", "% attained", "Level (0-3)"};
            Row h = co.createRow(0);
            for (int i = 0; i < ch.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(ch[i]);
                c.setCellStyle(head);
            }
            int rr = 1;
            for (CoStat cs : m.coStats()) {
                Row row = co.createRow(rr++);
                row.createCell(0).setCellValue(safe(cs.code()));
                row.createCell(1).setCellValue(safe(nz(cs.description())));
                row.createCell(2).setCellValue(cs.target().doubleValue());
                row.createCell(3).setCellValue(cs.students());
                row.createCell(4).setCellValue(cs.attained());
                row.createCell(5).setCellValue(cs.attainedPercent().doubleValue());
                row.createCell(6).setCellValue(cs.level());
            }
            co.createRow(rr + 1).createCell(0).setCellValue("A student attains a CO when the marks on that CO's questions reach the CO target. Level 3: 70% or more of students attained, 2: 60%, 1: 50%, otherwise 0.");
            co.setColumnWidth(1, 60 * 256);
            for (int i : new int[] {0, 2, 3, 4, 5, 6}) {
                co.setColumnWidth(i, 16 * 256);
            }

            Sheet qk = wb.createSheet("Question key");
            String[] qh = {"Question", "Text", "CO", "Bloom's level", "Unit", "Max marks"};
            Row qr = qk.createRow(0);
            for (int i = 0; i < qh.length; i++) {
                Cell c = qr.createCell(i);
                c.setCellValue(qh[i]);
                c.setCellStyle(head);
            }
            int k = 1;
            for (Col c : m.cols()) {
                Row row = qk.createRow(k++);
                row.createCell(0).setCellValue(c.label());
                row.createCell(1).setCellValue(safe(c.text().length() > 300 ? c.text().substring(0, 300) : c.text()));
                row.createCell(2).setCellValue(safe(nz(c.co())));
                row.createCell(3).setCellValue(safe(nz(c.bloom())));
                row.createCell(4).setCellValue(safe(nz(c.unit())));
                row.createCell(5).setCellValue(c.max().doubleValue());
            }
            qk.setColumnWidth(1, 80 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw AppException.bad("Could not create the Excel file.");
        }
    }

    // ------------------------------------------------------------------ PDF
    @Transactional(readOnly = true)
    public byte[] pdf(AuthUser user, Long examId) {
        Matrix m = build(user, examId);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 7);
        Font smallBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7);
        doc.add(new Paragraph("Student-wise question-wise marks: " + m.exam().getTitle(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13)));
        doc.add(new Paragraph(m.exam().getSubject().getName() + "   |   Students: " + m.students().size() + "   |   Best finished attempt per student", FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY)));
        doc.add(new Paragraph(" "));
        if (m.students().isEmpty()) {
            doc.add(new Paragraph("No graded attempts yet.", FontFactory.getFont(FontFactory.HELVETICA, 10)));
        }
        final int perBlock = 14;
        for (int from = 0; from < m.cols().size() && !m.students().isEmpty(); from += perBlock) {
            int to = Math.min(m.cols().size(), from + perBlock);
            boolean last = to == m.cols().size();
            int n = to - from;
            int extra = last ? 3 + m.coCodes().size() : 0;
            float[] widths = new float[3 + n + extra];
            widths[0] = 1.2f;
            widths[1] = 3f;
            widths[2] = 5f;
            for (int i = 3; i < widths.length; i++) {
                widths[i] = 1.6f;
            }
            PdfPTable t = new PdfPTable(widths);
            t.setWidthPercentage(100);
            t.setHeaderRows(4);
            String[] meta = {"Question", "CO", "Bloom's", "Max"};
            for (int i = 0; i < meta.length; i++) {
                cell(t, "", smallBold, true);
                cell(t, "", smallBold, true);
                cell(t, meta[i], smallBold, true);
                for (int q = from; q < to; q++) {
                    Col c = m.cols().get(q);
                    String v = i == 0 ? c.label() : i == 1 ? nz(c.co()) : i == 2 ? shortBloom(c.bloom()) : c.max().stripTrailingZeros().toPlainString();
                    cell(t, v, smallBold, true);
                }
                if (last) {
                    String[] tail = i == 0 ? new String[] {"Total", "Max", "%"} : new String[] {"", "", ""};
                    for (String x : tail) {
                        cell(t, x, smallBold, true);
                    }
                    for (String code : m.coCodes()) {
                        cell(t, i == 0 ? code + " %" : "", smallBold, true);
                    }
                }
            }
            int sl = 1;
            for (Stu st : m.students()) {
                cell(t, String.valueOf(sl++), small, false);
                cell(t, nz(st.roll()), small, false);
                PdfPCell nm = new PdfPCell(new Phrase(nz(st.name()), small));
                t.addCell(nm);
                for (int q = from; q < to; q++) {
                    String key = m.cols().get(q).key();
                    BigDecimal v = st.marks().get(key);
                    cell(t, v != null ? v.stripTrailingZeros().toPlainString() : (st.pending().contains(key) ? "P" : "-"), small, false);
                }
                if (last) {
                    cell(t, st.total().stripTrailingZeros().toPlainString(), smallBold, false);
                    cell(t, st.max().stripTrailingZeros().toPlainString(), small, false);
                    cell(t, st.percent().stripTrailingZeros().toPlainString(), small, false);
                    for (String code : m.coCodes()) {
                        BigDecimal p = st.coPercent().get(code);
                        cell(t, p == null ? "-" : p.setScale(0, RoundingMode.HALF_UP).toPlainString(), small, false);
                    }
                }
            }
            String[] foot = {"Students answered", "Average marks", "% at/above target"};
            for (int i = 0; i < foot.length; i++) {
                cell(t, "", smallBold, true);
                cell(t, "", smallBold, true);
                cell(t, foot[i], smallBold, true);
                for (int q = from; q < to; q++) {
                    QStat qs = m.qStats().get(m.cols().get(q).key());
                    String v = i == 0 ? String.valueOf(qs.students()) : i == 1 ? qs.average().stripTrailingZeros().toPlainString() : qs.attainedPercent().stripTrailingZeros().toPlainString();
                    cell(t, v, smallBold, true);
                }
                for (int x = 0; x < extra; x++) {
                    cell(t, "", smallBold, true);
                }
            }
            doc.add(t);
            doc.add(new Paragraph(" "));
        }
        doc.add(new Paragraph("Course Outcome attainment", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        PdfPTable ct = new PdfPTable(new float[] {1.2f, 6f, 1.4f, 1.4f, 1.6f, 1.6f, 1.4f});
        ct.setWidthPercentage(100);
        for (String h : new String[] {"CO", "Description", "Target %", "Students", "Attained", "% attained", "Level"}) {
            cell(ct, h, smallBold, true);
        }
        for (CoStat cs : m.coStats()) {
            cell(ct, cs.code(), small, false);
            ct.addCell(new PdfPCell(new Phrase(nz(cs.description()), small)));
            cell(ct, cs.target().stripTrailingZeros().toPlainString(), small, false);
            cell(ct, String.valueOf(cs.students()), small, false);
            cell(ct, String.valueOf(cs.attained()), small, false);
            cell(ct, cs.attainedPercent().stripTrailingZeros().toPlainString(), small, false);
            cell(ct, String.valueOf(cs.level()), small, false);
        }
        doc.add(ct);
        doc.add(new Paragraph("A student attains a CO when the marks on that CO's questions reach the CO target. Level 3: 70% or more of students attained, 2: 60%, 1: 50%, otherwise 0. P = marks pending manual grading, - = question not in the student's paper.", FontFactory.getFont(FontFactory.HELVETICA, 7, Color.DARK_GRAY)));
        doc.close();
        return out.toByteArray();
    }

    private static void cell(PdfPTable t, String text, Font f, boolean header) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        if (header) {
            c.setBackgroundColor(new Color(0xE6, 0xEA, 0xF0));
        }
        t.addCell(c);
    }

    private static String bloomLabel(String b) {
        if (b == null || b.isBlank()) {
            return null;
        }
        String l = b.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    private static String shortBloom(String b) {
        return b == null ? "" : b.length() > 5 ? b.substring(0, 5) : b;
    }

    private static BigDecimal pct(BigDecimal got, BigDecimal max) {
        return max.signum() == 0 ? BigDecimal.ZERO : got.multiply(BigDecimal.valueOf(100)).divide(max, 1, RoundingMode.HALF_UP);
    }

    private static void put(Sheet s, int row, int col, String v, CellStyle style) {
        Cell c = s.createRow(row).createCell(col);
        c.setCellValue(safe(v));
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String safe(String v) {
        if (v == null) {
            return "";
        }
        return !v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 ? "'" + v : v;
    }
}
