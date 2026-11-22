package com.mindbridgex.assessment.service;

import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mindbridgex.assessment.security.AuthUser;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CO / PO attainment (NBA / NAAC style) computed from real student results.
 * <ul>
 * <li>A student's CO score = marks earned / marks available on the questions tagged with that CO (best attempt per exam;
 * questions dropped by a live correction are left out).</li>
 * <li>A student attains a CO when the CO score reaches the CO's target (default 60%).</li>
 * <li>CO attainment level = 3 if at least 70% of students attained it, 2 if at least 60%, 1 if at least 50%, else 0.</li>
 * <li>PO attainment = average of the mapped CO levels, weighted by the mapping strength (1 low, 2 medium, 3 high).</li>
 * </ul>
 */
@Service
public class OutcomeReportService {
    private final JdbcClient jdbc;
    private final AccessService access;

    public record Scope(Long subjectId, Long batchId, Long examId) {
    }

    public record CoRow(Long id, String code, String description, BigDecimal target, int students, int attained, BigDecimal attainedPercent, BigDecimal averagePercent, int level) {
    }

    public record PoRow(String code, String description, BigDecimal level, String contributors) {
    }

    public record StudentRow(Long id, String name, String username, Map<String, BigDecimal> coPercent, BigDecimal overall) {
    }

    public record BloomRow(String level, int students, BigDecimal averagePercent) {
    }

    public record Report(String subject, String subjectCode, String scopeLabel, List<CoRow> cos, List<PoRow> pos, List<StudentRow> students, List<String> coCodes, List<BloomRow> bloom) {
    }

    public OutcomeReportService(JdbcClient jdbc, AccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public static int levelFor(BigDecimal attainedPercent) {
        if (attainedPercent == null) {
            return 0;
        }
        double p = attainedPercent.doubleValue();
        return p >= 70 ? 3 : p >= 60 ? 2 : p >= 50 ? 1 : 0;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> examsOf(AuthUser user, Long subjectId) {
        access.requireSubject(user, subjectId);
        return jdbc.sql("select id, title from exams where subject_id = :s order by id desc").param("s", subjectId).query().listOfRows();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> batchesOf(AuthUser user, Long subjectId) {
        access.requireSubject(user, subjectId);
        return jdbc.sql("select id, name from batches where active order by name").query().listOfRows();
    }

    @Transactional(readOnly = true)
    public Report build(AuthUser user, Scope scope) {
        access.requireSubject(user, scope.subjectId());
        Map<String, Object> subj = jdbc.sql("select name, code from subjects where id = :s").param("s", scope.subjectId()).query().singleRow();
        record Co(Long id, String code, String description, BigDecimal target) {
        }
        List<Co> cos = jdbc.sql("select id, code, description, target_percent from course_outcomes where subject_id = :s order by code").param("s", scope.subjectId())
                .query((rs, n) -> new Co(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4))).list();
        String sql = """
                with best as (
                  select distinct on (a.exam_id, a.student_id) a.id attempt_id, a.exam_id, a.student_id
                  from attempts a join grades g on g.attempt_id = a.id join exams e on e.id = a.exam_id
                  where a.status <> 'IN_PROGRESS' and e.subject_id = :subj and (cast(:exam as bigint) is null or a.exam_id = :exam)
                  order by a.exam_id, a.student_id, g.percentage desc, a.id desc)
                select b.student_id, coalesce(aq.co_id, q.co_id) co_id, sum(coalesce(ans.awarded_marks, 0)) awarded, sum(aq.marks) maxm
                from best b join attempt_questions aq on aq.attempt_id = b.attempt_id
                left join questions q on q.id = aq.source_question_id
                left join attempt_answers ans on ans.attempt_question_id = aq.id
                where aq.void_mode <> 'DROPPED' and coalesce(aq.co_id, q.co_id) is not null
                  and not (coalesce(ans.needs_manual, false) and ans.awarded_marks is null)
                  and (cast(:batch as bigint) is null or b.student_id in (select student_id from batch_students where batch_id = :batch))
                group by b.student_id, coalesce(aq.co_id, q.co_id)
                """;
        Map<Long, Map<Long, BigDecimal[]>> byStudent = new LinkedHashMap<>();
        jdbc.sql(sql).param("subj", scope.subjectId()).param("exam", scope.examId(), java.sql.Types.BIGINT).param("batch", scope.batchId(), java.sql.Types.BIGINT).query((rs, n) -> {
            byStudent.computeIfAbsent(rs.getLong(1), k -> new LinkedHashMap<>()).merge(rs.getLong(2), new BigDecimal[] {rs.getBigDecimal(3), rs.getBigDecimal(4)},
                    (x, y) -> new BigDecimal[] {x[0].add(y[0]), x[1].add(y[1])});
            return 0;
        }).list();

        Map<Long, String[]> names = new LinkedHashMap<>();
        if (!byStudent.isEmpty()) {
            jdbc.sql("select id, full_name, username from users where id in (:ids) order by full_name").param("ids", byStudent.keySet()).query((rs, n) -> {
                names.put(rs.getLong(1), new String[] {rs.getString(2), rs.getString(3)});
                return 0;
            }).list();
        }
        List<CoRow> coRows = new ArrayList<>();
        Map<Long, Integer> levels = new LinkedHashMap<>();
        for (Co co : cos) {
            int n = 0;
            int ok = 0;
            BigDecimal sum = BigDecimal.ZERO;
            for (Map<Long, BigDecimal[]> m : byStudent.values()) {
                BigDecimal[] v = m.get(co.id());
                if (v == null || v[1].signum() == 0) {
                    continue;
                }
                BigDecimal pct = pct(v[0], v[1]);
                n++;
                sum = sum.add(pct);
                if (pct.compareTo(co.target()) >= 0) {
                    ok++;
                }
            }
            BigDecimal attainedPct = n == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(ok * 100.0 / n).setScale(1, RoundingMode.HALF_UP);
            BigDecimal avg = n == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(n), 1, RoundingMode.HALF_UP);
            int level = n == 0 ? 0 : levelFor(attainedPct);
            levels.put(co.id(), n == 0 ? null : level);
            coRows.add(new CoRow(co.id(), co.code(), co.description(), co.target(), n, ok, attainedPct, avg, level));
        }
        // PO attainment
        Map<String, double[]> po = new LinkedHashMap<>();
        Map<String, String[]> poInfo = new LinkedHashMap<>();
        Map<String, StringBuilder> contrib = new LinkedHashMap<>();
        jdbc.sql("""
                select c.id, p.code, coalesce(p.description,''), m.level, c.code from co_po_map m join course_outcomes c on c.id = m.co_id join program_outcomes p on p.id = m.po_id
                where c.subject_id = :s order by p.code, c.code
                """).param("s", scope.subjectId()).query((rs, n) -> {
            Integer lv = levels.get(rs.getLong(1));
            String key = rs.getString(2);
            poInfo.putIfAbsent(key, new String[] {rs.getString(3)});
            po.putIfAbsent(key, new double[2]);
            contrib.computeIfAbsent(key, k -> new StringBuilder());
            if (lv != null) {
                double w = rs.getInt(4);
                po.get(key)[0] += lv * w;
                po.get(key)[1] += w;
                contrib.get(key).append(contrib.get(key).length() == 0 ? "" : ", ").append(rs.getString(5)).append(" (weight ").append(rs.getInt(4)).append(")");
            }
            return 0;
        }).list();
        List<PoRow> poRows = new ArrayList<>();
        po.forEach((code, v) -> poRows.add(new PoRow(code, poInfo.get(code)[0], v[1] == 0 ? null : BigDecimal.valueOf(v[0] / v[1]).setScale(2, RoundingMode.HALF_UP), contrib.get(code).toString())));

        List<StudentRow> students = new ArrayList<>();
        for (Map.Entry<Long, String[]> e : names.entrySet()) {
            Map<Long, BigDecimal[]> m = byStudent.get(e.getKey());
            Map<String, BigDecimal> pcts = new LinkedHashMap<>();
            BigDecimal a = BigDecimal.ZERO;
            BigDecimal b = BigDecimal.ZERO;
            for (Co co : cos) {
                BigDecimal[] v = m.get(co.id());
                pcts.put(co.code(), v == null || v[1].signum() == 0 ? null : pct(v[0], v[1]));
                if (v != null) {
                    a = a.add(v[0]);
                    b = b.add(v[1]);
                }
            }
            students.add(new StudentRow(e.getKey(), e.getValue()[0], e.getValue()[1], pcts, b.signum() == 0 ? null : pct(a, b)));
        }
        String label = "Course" + (scope.examId() != null ? ", one exam" : ", all exams") + (scope.batchId() != null ? ", one class" : ", all students");
        List<BloomRow> bloomRows = new ArrayList<>();
        String bsql = sql.substring(0, sql.indexOf("select b.student_id"))
                + """
                select initcap(lower(q.bloom)), count(distinct b.student_id), avg(case when aq.marks > 0 then coalesce(ans.awarded_marks, 0) * 100.0 / aq.marks end)
                from best b join attempt_questions aq on aq.attempt_id = b.attempt_id
                join questions q on q.id = aq.source_question_id
                left join attempt_answers ans on ans.attempt_question_id = aq.id
                where aq.void_mode <> 'DROPPED' and q.bloom is not null
                  and not (coalesce(ans.needs_manual, false) and ans.awarded_marks is null)
                  and (cast(:batch as bigint) is null or b.student_id in (select student_id from batch_students where batch_id = :batch))
                group by q.bloom order by min(case q.bloom when 'REMEMBER' then 1 when 'UNDERSTAND' then 2 when 'APPLY' then 3 when 'ANALYZE' then 4 when 'EVALUATE' then 5 else 6 end)
                """;
        jdbc.sql(bsql).param("subj", scope.subjectId()).param("exam", scope.examId(), java.sql.Types.BIGINT).param("batch", scope.batchId(), java.sql.Types.BIGINT).query((rs, n) -> {
            bloomRows.add(new BloomRow(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3).setScale(1, RoundingMode.HALF_UP)));
            return 0;
        }).list();
        return new Report((String) subj.get("name"), (String) subj.get("code"), label, coRows, poRows, students, cos.stream().map(Co::code).toList(), bloomRows);
    }

    private static BigDecimal pct(BigDecimal got, BigDecimal max) {
        return got.multiply(BigDecimal.valueOf(100)).divide(max, 1, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ Excel
    public byte[] excel(Report r) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            head.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            Sheet s1 = wb.createSheet("CO attainment");
            line(s1, 0, head, r.subject() + " (" + r.subjectCode() + ") - " + r.scopeLabel());
            row(s1, 2, head, "CO", "Description", "Target %", "Students", "Attained", "Attained %", "Average %", "Level (0-3)");
            int i = 3;
            for (CoRow c : r.cos()) {
                row(s1, i++, null, c.code(), c.description(), c.target(), c.students(), c.attained(), c.attainedPercent(), c.averagePercent(), c.level());
            }
            Sheet s2 = wb.createSheet("PO attainment");
            row(s2, 0, head, "PO", "Description", "Attainment (0-3)", "From");
            i = 1;
            for (PoRow p : r.pos()) {
                row(s2, i++, null, p.code(), p.description(), p.level(), p.contributors());
            }
            Sheet s4 = wb.createSheet("Bloom level");
            row(s4, 0, head, "Bloom's level", "Students", "Average score %");
            int bi = 1;
            for (BloomRow b : r.bloom()) {
                row(s4, bi++, null, b.level(), b.students(), b.averagePercent());
            }
            Sheet s3 = wb.createSheet("Students");
            List<Object> h = new ArrayList<>(List.of("Student", "Username"));
            r.coCodes().forEach(c -> h.add(c + " %"));
            h.add("Overall %");
            row(s3, 0, head, h.toArray());
            i = 1;
            for (StudentRow s : r.students()) {
                List<Object> v = new ArrayList<>(List.of(s.name(), s.username()));
                r.coCodes().forEach(c -> v.add(s.coPercent().get(c)));
                v.add(s.overall());
                row(s3, i++, null, v.toArray());
            }
            for (Sheet s : List.of(s1, s2, s3, s4)) {
                for (int c = 0; c < 12; c++) {
                    s.setColumnWidth(c, 4200);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void line(Sheet s, int r, CellStyle st, String text) {
        Row row = s.createRow(r);
        row.createCell(0).setCellValue(text);
        row.getCell(0).setCellStyle(st);
    }

    private static void row(Sheet s, int r, CellStyle st, Object... vals) {
        Row row = s.createRow(r);
        for (int c = 0; c < vals.length; c++) {
            org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
            Object v = vals[c];
            if (v == null) {
                cell.setBlank();
            } else if (v instanceof BigDecimal b) {
                cell.setCellValue(b.doubleValue());
            } else if (v instanceof Number n) {
                cell.setCellValue(n.doubleValue());
            } else {
                cell.setCellValue(v.toString());
            }
            if (st != null) {
                cell.setCellStyle(st);
            }
        }
    }

    // ------------------------------------------------------------------ PDF
    public byte[] pdf(Report r, Long onlyStudent) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        com.lowagie.text.Font body = FontFactory.getFont(FontFactory.HELVETICA, 9);
        doc.add(new Paragraph("CO-PO attainment: " + r.subject() + " (" + r.subjectCode() + ")", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
        doc.add(new Paragraph(r.scopeLabel(), FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));
        doc.add(new Paragraph(" "));
        if (onlyStudent == null) {
            doc.add(new Paragraph("Course Outcome attainment", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            PdfPTable t = table(new float[] {1, 5, 1, 1, 1, 1.2f, 1.2f, 1}, "CO", "Description", "Target %", "Students", "Attained", "Attained %", "Average %", "Level");
            for (CoRow c : r.cos()) {
                cells(t, body, c.code(), c.description(), c.target().stripTrailingZeros().toPlainString(), c.students(), c.attained(), c.attainedPercent(), c.averagePercent(), c.level());
            }
            doc.add(t);
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Programme Outcome attainment (0 to 3)", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            PdfPTable p = table(new float[] {1, 5, 1.5f, 4}, "PO", "Description", "Attainment", "From");
            for (PoRow x : r.pos()) {
                cells(p, body, x.code(), x.description(), x.level() == null ? "-" : x.level(), x.contributors());
            }
            doc.add(p);
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Performance by Bloom's level", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            PdfPTable bt = table(new float[] {3, 1.5f, 2}, "Bloom's level", "Students", "Average score %");
            for (BloomRow b : r.bloom()) {
                cells(bt, body, b.level(), b.students(), b.averagePercent());
            }
            doc.add(bt);
            doc.add(new Paragraph(" "));
        }
        doc.add(new Paragraph(onlyStudent == null ? "Students (CO score %)" : "Student CO scores (%)", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        List<String> heads = new ArrayList<>(List.of("Student", "Username"));
        r.coCodes().forEach(heads::add);
        heads.add("Overall");
        float[] widths = new float[heads.size()];
        java.util.Arrays.fill(widths, 1f);
        widths[0] = 3f;
        widths[1] = 2f;
        PdfPTable st = table(widths, heads.toArray(new String[0]));
        for (StudentRow s : r.students()) {
            if (onlyStudent != null && !onlyStudent.equals(s.id())) {
                continue;
            }
            List<Object> v = new ArrayList<>(List.of(s.name(), s.username()));
            r.coCodes().forEach(c -> v.add(s.coPercent().get(c) == null ? "-" : s.coPercent().get(c)));
            v.add(s.overall() == null ? "-" : s.overall());
            cells(st, body, v.toArray());
        }
        doc.add(st);
        doc.close();
        return out.toByteArray();
    }

    private static PdfPTable table(float[] widths, String... heads) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        for (String h : heads) {
            PdfPCell c = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
            c.setBackgroundColor(new Color(230, 233, 245));
            t.addCell(c);
        }
        return t;
    }

    private static void cells(PdfPTable t, com.lowagie.text.Font f, Object... vals) {
        for (Object v : vals) {
            t.addCell(new Phrase(v == null ? "-" : v.toString(), f));
        }
    }
}
