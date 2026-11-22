package com.mindbridgex.assessment.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.domain.AttemptStatus;
import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.dto.ExamResultRow;
import com.mindbridgex.assessment.dto.QuestionStat;
import com.mindbridgex.assessment.entity.*;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.*;
import com.mindbridgex.assessment.security.AuthUser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    public record Summary(Exam exam, BigDecimal totalMarks, long assigned, long attempted, long submitted, long inProgress, long pendingManual, BigDecimal average, BigDecimal highest, BigDecimal lowest, BigDecimal passRate, int[] buckets, List<ExamResultRow> rows, List<QuestionStat> stats) {
    }

    private final ExamRepository exams;
    private final AttemptRepository attempts;
    private final AttemptQuestionRepository attemptQuestions;
    private final ExamAssignmentRepository assignments;
    private final BatchRepository batches;
    private final GradeRepository grades;
    private final UserRepository users;
    private final ExamService examService;
    private final AccessService access;
    private final AppProperties props;

    @Transactional(readOnly = true)
    public Exam examFor(AuthUser user, Long examId) {
        Exam e = exams.findById(examId).orElseThrow(() -> AppException.notFound("Exam"));
        if (!user.isAdmin()) {
            access.requireSubject(user, e.getSubject().getId());
        }
        return e;
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Exam> examsFor(AuthUser user, int page, int size) {
        if (user.isAdmin()) {
            return exams.findAllByOrderByStartAtDesc(PageRequest.of(Math.max(page, 0), size));
        }
        Set<Long> ids = access.teacherSubjectIds(user);
        if (ids.isEmpty()) {
            return org.springframework.data.domain.Page.empty();
        }
        return exams.findBySubjectIdInOrderByStartAtDesc(ids, PageRequest.of(Math.max(page, 0), size));
    }

    @Transactional(readOnly = true)
    public Summary summary(AuthUser user, Long examId) {
        Exam e = examFor(user, examId);
        List<ExamResultRow> rows = attempts.findResultRows(examId);
        Set<Long> assigned = new HashSet<>();
        for (ExamAssignment a : assignments.findByExamIdOrderByIdAsc(examId)) {
            if (a.getStudentId() != null) {
                assigned.add(a.getStudentId());
            } else if (a.getBatchId() != null) {
                batches.findById(a.getBatchId()).ifPresent(b -> assigned.addAll(b.getStudentIds()));
            }
        }
        long inProgress = rows.stream().filter(r -> r.status() == AttemptStatus.IN_PROGRESS).count();
        long submitted = rows.size() - inProgress;
        long pending = rows.stream().filter(r -> r.pendingManual() != null && r.pendingManual() > 0).count();
        // statistics use each student's best finished attempt
        Map<Long, ExamResultRow> best = new HashMap<>();
        for (ExamResultRow r : rows) {
            if (r.score() == null) {
                continue;
            }
            best.merge(r.studentId(), r, (x, y) -> y.score().compareTo(x.score()) > 0 ? y : x);
        }
        BigDecimal avg = BigDecimal.ZERO;
        BigDecimal hi = BigDecimal.ZERO;
        BigDecimal lo = BigDecimal.ZERO;
        BigDecimal passRate = BigDecimal.ZERO;
        int[] buckets = new int[10];
        if (!best.isEmpty()) {
            List<ExamResultRow> b = new ArrayList<>(best.values());
            avg = b.stream().map(ExamResultRow::percentage).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(b.size()), 2, RoundingMode.HALF_UP);
            hi = b.stream().map(ExamResultRow::score).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            lo = b.stream().map(ExamResultRow::score).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            long passed = b.stream().filter(r -> Boolean.TRUE.equals(r.passed())).count();
            passRate = BigDecimal.valueOf(passed * 100.0 / b.size()).setScale(1, RoundingMode.HALF_UP);
            for (ExamResultRow r : b) {
                int idx = Math.min(9, r.percentage().divide(BigDecimal.TEN, 0, RoundingMode.DOWN).intValue());
                buckets[Math.max(0, idx)]++;
            }
        }
        return new Summary(e, examService.totalMarks(examId), assigned.size(), best.size(), submitted, inProgress, pending, avg, hi, lo, passRate, buckets, rows, attemptQuestions.questionStats(examId));
    }

    // ------------------------------------------------------------------ exports
    @Transactional(readOnly = true)
    public byte[] excel(AuthUser user, Long examId) {
        Summary s = summary(user, examId);
        try (
            XSSFWorkbook wb = new XSSFWorkbook();
            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Sheet sum = wb.createSheet("Summary");
            String[][] kv = {{"Exam", s.exam().getTitle()}, {"Subject", s.exam().getSubject().getName()}, {"Total marks", s.totalMarks().toPlainString()}, {"Assigned students", String.valueOf(s.assigned())}, {"Students attempted", String.valueOf(s.attempted())}, {"Average %", s.average().toPlainString()}, {"Highest score", s.highest().toPlainString()}, {"Lowest score", s.lowest().toPlainString()}, {"Pass rate %", s.passRate().toPlainString()}};
            for (int i = 0; i < kv.length; i++) {
                Row r = sum.createRow(i);
                Cell k = r.createCell(0);
                k.setCellValue(kv[i][0]);
                k.setCellStyle(head);
                r.createCell(1).setCellValue(safe(kv[i][1]));
            }
            sum.autoSizeColumn(0);
            sum.autoSizeColumn(1);
            Sheet res = wb.createSheet("Results");
            String[] cols = {"Student", "Roll no", "Attempt", "Status", "Submitted", "Score", "Max", "Percentage", "Result", "Focus losses"};
            Row h = res.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(head);
            }
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int n = 1;
            for (ExamResultRow r : s.rows()) {
                Row row = res.createRow(n++);
                row.createCell(0).setCellValue(safe(r.studentName()));
                row.createCell(1).setCellValue(safe(r.rollNo()));
                row.createCell(2).setCellValue(r.attemptNo());
                row.createCell(3).setCellValue(r.status().getLabel());
                row.createCell(4).setCellValue(r.submittedAt() == null ? "" : f.format(ZonedDateTime.ofInstant(r.submittedAt(), props.zone())));
                if (r.score() != null) {
                    row.createCell(5).setCellValue(r.score().doubleValue());
                    row.createCell(6).setCellValue(r.maxScore().doubleValue());
                    row.createCell(7).setCellValue(r.percentage().doubleValue());
                    row.createCell(8).setCellValue(r.pendingManual() != null && r.pendingManual() > 0 ? "Pending" : (Boolean.TRUE.equals(r.passed()) ? "Pass" : "Fail"));
                }
                row.createCell(9).setCellValue(r.focusLosses());
            }
            for (int i = 0; i < cols.length; i++) {
                res.autoSizeColumn(i);
            }
            Sheet qa = wb.createSheet("Question analysis");
            String[] qc = {"Question", "Type", "Attempts", "Answered %", "Correct %", "Average marks", "Max marks", "Difficulty"};
            Row qh = qa.createRow(0);
            for (int i = 0; i < qc.length; i++) {
                Cell c = qh.createCell(i);
                c.setCellValue(qc[i]);
                c.setCellStyle(head);
            }
            int qn = 1;
            for (QuestionStat q : s.stats()) {
                Row row = qa.createRow(qn++);
                row.createCell(0).setCellValue(safe(q.text().length() > 200 ? q.text().substring(0, 200) : q.text()));
                row.createCell(1).setCellValue(q.type().getLabel());
                row.createCell(2).setCellValue(q.attempts());
                row.createCell(3).setCellValue(q.percentAnswered().doubleValue());
                row.createCell(4).setCellValue(q.percentCorrect().doubleValue());
                row.createCell(5).setCellValue(q.avgMarksRounded().doubleValue());
                row.createCell(6).setCellValue(q.maxMarks().doubleValue());
                row.createCell(7).setCellValue(q.band());
            }
            qa.setColumnWidth(0, 60 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw AppException.bad("Could not create the Excel file.");
        }
    }

    @Transactional(readOnly = true)
    public byte[] pdf(AuthUser user, Long examId) {
        Summary s = summary(user, examId);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph(s.exam().getTitle(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
        doc.add(new Paragraph(s.exam().getSubject().getName() + "  |  Total marks " + s.totalMarks().stripTrailingZeros().toPlainString() + "  |  Attempted " + s.attempted() + " of " + s.assigned() + "  |  Average " + s.average() + "%  |  Pass rate " + s.passRate() + "%", FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));
        doc.add(new Paragraph(" "));
        PdfPTable t = new PdfPTable(new float[] {4, 2, 1, 2, 2, 2, 2});
        t.setWidthPercentage(100);
        for (String h : new String[] {"Student", "Roll no", "Att.", "Status", "Score", "%", "Result"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
            c.setBackgroundColor(new Color(15133429));
            t.addCell(c);
        }
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 9);
        for (ExamResultRow r : s.rows()) {
            t.addCell(new Phrase(nz(r.studentName()), body));
            t.addCell(new Phrase(nz(r.rollNo()), body));
            t.addCell(new Phrase(String.valueOf(r.attemptNo()), body));
            t.addCell(new Phrase(r.status().getLabel(), body));
            t.addCell(new Phrase(r.score() == null ? "-" : r.score().stripTrailingZeros().toPlainString() + " / " + r.maxScore().stripTrailingZeros().toPlainString(), body));
            t.addCell(new Phrase(r.percentage() == null ? "-" : r.percentage().toPlainString(), body));
            t.addCell(new Phrase(r.score() == null ? "-" : (r.pendingManual() != null && r.pendingManual() > 0 ? "Pending" : (Boolean.TRUE.equals(r.passed()) ? "Pass" : "Fail")), body));
        }
        doc.add(t);
        doc.close();
        return out.toByteArray();
    }

    /**
     * One-page scorecard for a single attempt. Authorisation is the caller's job.
     */
    @Transactional(readOnly = true)
    public byte[] scorecard(Long attemptId) {
        Attempt a = attempts.findById(attemptId).orElseThrow(() -> AppException.notFound("Attempt"));
        Exam e = exams.findById(a.getExamId()).orElseThrow();
        Grade g = grades.findByAttemptId(attemptId).orElseThrow(() -> AppException.notFound("Result"));
        User u = users.findById(a.getStudentId()).orElseThrow();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 54, 54, 54, 54);
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph("Score card", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new Color(2833036))));
        doc.add(new Paragraph(" "));
        PdfPTable t = new PdfPTable(new float[] {2, 4});
        t.setWidthPercentage(100);
        String[][] rows = {{"Student", u.getFullName()}, {"Roll no", nz(u.getRollNo())}, {"Exam", e.getTitle()}, {"Subject", e.getSubject().getName()}, {"Attempt", "#" + a.getAttemptNo()}, {"Submitted", a.getSubmittedAt() == null ? "-" : DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").format(ZonedDateTime.ofInstant(a.getSubmittedAt(), props.zone()))}, {"Score", g.getTotalScore().stripTrailingZeros().toPlainString() + " / " + g.getMaxScore().stripTrailingZeros().toPlainString()}, {"Percentage", g.getPercentage().toPlainString() + "%"}, {"Result", g.getPendingManual() > 0 ? "Pending manual grading" : (g.isPassed() ? "PASSED" : "NOT PASSED")}};
        Font lf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font vf = FontFactory.getFont(FontFactory.HELVETICA, 11);
        for (String[] r : rows) {
            PdfPCell k = new PdfPCell(new Phrase(r[0], lf));
            k.setPadding(6);
            PdfPCell v = new PdfPCell(new Phrase(r[1], vf));
            v.setPadding(6);
            t.addCell(k);
            t.addCell(v);
        }
        doc.add(t);
        doc.close();
        return out.toByteArray();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String safe(String v) {
        if (v == null) {
            return "";
        }
        return !v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 ? "\'" + v : v;
    }

    @Transactional(readOnly = true)
    public Optional<Attempt> attemptFor(AuthUser user, Long attemptId) {
        Attempt a = attempts.findById(attemptId).orElse(null);
        if (a == null) {
            return Optional.empty();
        }
        examFor(user, a.getExamId());
        return Optional.of(a);
    }

    public ReportService(final ExamRepository exams, final AttemptRepository attempts, final AttemptQuestionRepository attemptQuestions, final ExamAssignmentRepository assignments, final BatchRepository batches, final GradeRepository grades, final UserRepository users, final ExamService examService, final AccessService access, final AppProperties props) {
        this.exams = exams;
        this.attempts = attempts;
        this.attemptQuestions = attemptQuestions;
        this.assignments = assignments;
        this.batches = batches;
        this.grades = grades;
        this.users = users;
        this.examService = examService;
        this.access = access;
        this.props = props;
    }
}
