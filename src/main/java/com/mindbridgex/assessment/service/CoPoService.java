package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.security.AuthUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Course Outcomes (per subject), Program Outcomes (per branch) and the CO to PO mapping. */
@Service
public class CoPoService {
    private final JdbcClient jdbc;
    private final AccessService access;
    private final AuditService audit;

    public record Co(Long id, Long subjectId, String subjectName, String code, String description, BigDecimal targetPercent) {
    }

    public record Po(Long id, Long branchId, String branchName, String code, String description) {
    }

    public CoPoService(JdbcClient jdbc, AccessService access, AuditService audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
    }

    // ---------------------------------------------------------------- course outcomes
    @Transactional(readOnly = true)
    public List<Co> cosForUser(AuthUser user) {
        Collection<Long> ids = access.teacherSubjectIds(user);
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select c.id, c.subject_id, s.name subject_name, c.code, c.description, c.target_percent from course_outcomes c "
                + "join subjects s on s.id = c.subject_id where c.subject_id in (:ids) order by s.name, c.code")
                .param("ids", ids).query((rs, n) -> new Co(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6))).list();
    }

    @Transactional(readOnly = true)
    public List<Co> cosForSubject(AuthUser user, Long subjectId) {
        access.requireSubject(user, subjectId);
        return jdbc.sql("select c.id, c.subject_id, s.name subject_name, c.code, c.description, c.target_percent from course_outcomes c "
                + "join subjects s on s.id = c.subject_id where c.subject_id = :s order by c.code")
                .param("s", subjectId).query((rs, n) -> new Co(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6))).list();
    }

    @Transactional
    public void addCo(AuthUser user, Long subjectId, String code, String description, BigDecimal target) {
        access.requireSubject(user, subjectId);
        String c = clean(code, 20);
        if (c.isEmpty()) {
            throw AppException.bad("Enter a code for the Course Outcome, for example CO1.");
        }
        BigDecimal t = target == null ? new BigDecimal("60") : target;
        if (t.signum() < 0 || t.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw AppException.bad("The target must be between 0 and 100 percent.");
        }
        boolean exists = jdbc.sql("select count(*) from course_outcomes where subject_id = :s and lower(code) = lower(:c)").param("s", subjectId).param("c", c).query(Long.class).single() > 0;
        if (exists) {
            throw AppException.conflict("Course Outcome " + c + " already exists for this subject.");
        }
        jdbc.sql("insert into course_outcomes (subject_id, code, description, target_percent) values (:s, :c, :d, :t)")
                .param("s", subjectId).param("c", c).param("d", clean(description, 1000)).param("t", t).update();
        audit.log("CO_CREATE", "CourseOutcome", subjectId, c);
    }

    @Transactional
    public void deleteCo(AuthUser user, Long coId) {
        Long subjectId = jdbc.sql("select subject_id from course_outcomes where id = :id").param("id", coId).query(Long.class).optional().orElseThrow(() -> AppException.notFound("Course Outcome"));
        access.requireSubject(user, subjectId);
        jdbc.sql("delete from course_outcomes where id = :id").param("id", coId).update();
        audit.log("CO_DELETE", "CourseOutcome", coId, null);
    }

    // ---------------------------------------------------------------- program outcomes
    @Transactional(readOnly = true)
    public List<Po> allPos() {
        return jdbc.sql("select p.id, p.branch_id, b.name, p.code, p.description from program_outcomes p left join branches b on b.id = p.branch_id order by b.name nulls first, p.code")
                .query((rs, n) -> new Po(rs.getLong(1), (Long) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5))).list();
    }

    /** POs that apply to a subject: its branch's POs plus the institution-wide ones. */
    @Transactional(readOnly = true)
    public List<Po> posForSubject(Long subjectId) {
        return jdbc.sql("select p.id, p.branch_id, b.name, p.code, p.description from program_outcomes p left join branches b on b.id = p.branch_id "
                + "where p.branch_id is null or p.branch_id = (select branch_id from subjects where id = :s) order by p.code")
                .param("s", subjectId).query((rs, n) -> new Po(rs.getLong(1), (Long) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5))).list();
    }

    @Transactional
    public void addPo(Long branchId, String code, String description) {
        String c = clean(code, 20);
        if (c.isEmpty()) {
            throw AppException.bad("Enter a code for the Program Outcome, for example PO1.");
        }
        long dup = jdbc.sql("select count(*) from program_outcomes where coalesce(branch_id,0) = coalesce(:b,0) and lower(code) = lower(:c)").param("b", branchId).param("c", c).query(Long.class).single();
        if (dup > 0) {
            throw AppException.conflict("Program Outcome " + c + " already exists there.");
        }
        jdbc.sql("insert into program_outcomes (branch_id, code, description) values (:b, :c, :d)").param("b", branchId).param("c", c).param("d", clean(description, 1000)).update();
        audit.log("PO_CREATE", "ProgramOutcome", null, c);
    }

    @Transactional
    public void deletePo(Long id) {
        jdbc.sql("delete from program_outcomes where id = :id").param("id", id).update();
        audit.log("PO_DELETE", "ProgramOutcome", id, null);
    }

    // ---------------------------------------------------------------- mapping
    /** key "coId:poId" -> level (1..3) */
    @Transactional(readOnly = true)
    public Map<String, Integer> mapping(Long subjectId) {
        Map<String, Integer> out = new HashMap<>();
        jdbc.sql("select m.co_id, m.po_id, m.level from co_po_map m join course_outcomes c on c.id = m.co_id where c.subject_id = :s")
                .param("s", subjectId).query((rs, n) -> {
                    out.put(rs.getLong(1) + ":" + rs.getLong(2), rs.getInt(3));
                    return 0;
                }).list();
        return out;
    }

    /** Replaces the CO-PO matrix of a subject. Parameters are named m_{coId}_{poId} with a value of 0 to 3 (0 = no link). */
    @Transactional
    public void saveMapping(AuthUser user, Long subjectId, Map<String, String> params) {
        access.requireSubject(user, subjectId);
        List<Co> cos = cosForSubject(user, subjectId);
        List<Po> pos = posForSubject(subjectId);
        jdbc.sql("delete from co_po_map where co_id in (select id from course_outcomes where subject_id = :s)").param("s", subjectId).update();
        int n = 0;
        for (Co co : cos) {
            for (Po po : pos) {
                String v = params.get("m_" + co.id() + "_" + po.id());
                int level = 0;
                try {
                    level = v == null || v.isBlank() ? 0 : Integer.parseInt(v.trim());
                } catch (NumberFormatException ignored) {
                    level = 0;
                }
                if (level >= 1 && level <= 3) {
                    jdbc.sql("insert into co_po_map (co_id, po_id, level) values (:c, :p, :l)").param("c", co.id()).param("p", po.id()).param("l", level).update();
                    n++;
                }
            }
        }
        audit.log("COPO_MAP_SAVE", "Subject", subjectId, n + " link(s)");
    }

    private static String clean(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    @Transactional(readOnly = true)
    public boolean hasCos(Long subjectId) {
        return jdbc.sql("select count(*) from course_outcomes where subject_id = :s").param("s", subjectId).query(Long.class).single() > 0;
    }

    /** Id of the Course Outcome with this code in the subject, or null. */
    @Transactional(readOnly = true)
    public Long coIdByCode(Long subjectId, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return jdbc.sql("select id from course_outcomes where subject_id = :s and lower(code) = lower(:c)").param("s", subjectId).param("c", code.trim())
                .query(Long.class).optional().orElse(null);
    }

    public record Cov(String label, BigDecimal marks, long count) {
    }

    /** Marks and question counts of the manually chosen questions, grouped by CO ("coverage") or Bloom level. */
    @Transactional(readOnly = true)
    public List<Cov> coverage(Long examId, boolean byBloom) {
        String key = byBloom ? "coalesce(initcap(lower(q.bloom)), 'Not tagged')" : "coalesce(c.code, 'No CO')";
        return jdbc.sql("select " + key + " k, sum(eq.marks), count(*) from exam_questions eq join questions q on q.id = eq.question_id left join course_outcomes c on c.id = q.co_id "
                + "where eq.exam_id = :e group by k order by k").param("e", examId)
                .query((rs, n) -> new Cov(rs.getString(1), rs.getBigDecimal(2), rs.getLong(3))).list();
    }
}
