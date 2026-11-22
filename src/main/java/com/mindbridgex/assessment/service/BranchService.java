package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.exception.AppException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Engineering branches / departments, and the cross-branch figures shown on the Super Admin dashboard. */
@Service
public class BranchService {
    private final JdbcClient jdbc;
    private final AuditService audit;

    public record Branch(Long id, String name, String code, boolean active) {
    }

    public record BranchStat(Long id, String name, String code, boolean active, long subjects, long batches, long teachers, long students, long exams, long attempts) {
    }

    public record Item(Long id, String label) {
    }

    public record Totals(long users, long teachers, long students, long admins, long branches, long exams, long liveAttempts, long rejected, long duplicateGroups) {
    }

    public BranchService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Branch> all() {
        return jdbc.sql("select id, name, code, active from branches order by name").query((rs, n) -> new Branch(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4))).list();
    }

    @Transactional(readOnly = true)
    public List<Branch> active() {
        return all().stream().filter(Branch::active).toList();
    }

    @Transactional(readOnly = true)
    public Branch get(Long id) {
        return jdbc.sql("select id, name, code, active from branches where id = :id").param("id", id)
                .query((rs, n) -> new Branch(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4))).optional().orElseThrow(() -> AppException.notFound("Branch"));
    }

    @Transactional
    public void create(String name, String code) {
        String n = name == null ? "" : name.trim();
        String c = code == null ? "" : code.trim().toUpperCase();
        if (n.isEmpty() || c.isEmpty() || n.length() > 120 || c.length() > 40) {
            throw AppException.bad("Enter a branch name and a short code, for example Computer Engineering and COMP.");
        }
        if (jdbc.sql("select count(*) from branches where lower(code) = lower(:c)").param("c", c).query(Long.class).single() > 0) {
            throw AppException.conflict("A branch with code " + c + " already exists.");
        }
        jdbc.sql("insert into branches (name, code) values (:n, :c)").param("n", n).param("c", c).update();
        audit.log("BRANCH_CREATE", "Branch", null, c);
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        jdbc.sql("update branches set active = :a where id = :id").param("a", active).param("id", id).update();
        audit.log(active ? "BRANCH_ACTIVATE" : "BRANCH_DEACTIVATE", "Branch", id, null);
    }

    // ---------------------------------------------------------------- membership
    @Transactional(readOnly = true)
    public List<Item> members(String kind, Long branchId) {
        return jdbc.sql(selectMembers(kind, true)).param("b", branchId).query((rs, n) -> new Item(rs.getLong(1), rs.getString(2))).list();
    }

    @Transactional(readOnly = true)
    public List<Item> candidates(String kind, Long branchId) {
        return jdbc.sql(selectMembers(kind, false)).param("b", branchId).query((rs, n) -> new Item(rs.getLong(1), rs.getString(2))).list();
    }

    private static String selectMembers(String kind, boolean in) {
        String cond = in ? "branch_id = :b" : "(branch_id is null or branch_id <> :b)";
        return switch (kind) {
            case "subject" -> "select id, name || ' (' || code || ')' from subjects where " + cond + " order by name";
            case "batch" -> "select id, name || ' (' || code || ')' from batches where " + cond + " order by name";
            case "teacher" -> "select u.id, u.full_name || ' (' || u.username || ')' from users u where " + cond.replace("branch_id", "u.branch_id")
                    + " and exists (select 1 from user_roles r where r.user_id = u.id and r.role_code = 'TEACHER') order by u.full_name";
            default -> throw AppException.bad("Unknown item type.");
        };
    }

    @Transactional
    public void assign(String kind, Long itemId, Long branchId) {
        String table = table(kind);
        jdbc.sql("update " + table + " set branch_id = :b where id = :id").param("b", branchId).param("id", itemId).update();
        audit.log(branchId == null ? "BRANCH_UNASSIGN" : "BRANCH_ASSIGN", "Branch", branchId, kind + " " + itemId);
    }

    private static String table(String kind) {
        return switch (kind) {
            case "subject" -> "subjects";
            case "batch" -> "batches";
            case "teacher" -> "users";
            default -> throw AppException.bad("Unknown item type.");
        };
    }

    // ---------------------------------------------------------------- dashboard
    @Transactional(readOnly = true)
    public List<BranchStat> stats() {
        return jdbc.sql("""
                select b.id, b.name, b.code, b.active,
                  (select count(*) from subjects s where s.branch_id = b.id),
                  (select count(*) from batches x where x.branch_id = b.id),
                  (select count(*) from users u where u.branch_id = b.id and exists (select 1 from user_roles r where r.user_id = u.id and r.role_code = 'TEACHER')),
                  (select count(distinct bs.student_id) from batch_students bs join batches x on x.id = bs.batch_id where x.branch_id = b.id),
                  (select count(*) from exams e join subjects s on s.id = e.subject_id where s.branch_id = b.id),
                  (select count(*) from attempts a join exams e on e.id = a.exam_id join subjects s on s.id = e.subject_id where s.branch_id = b.id and a.status <> 'IN_PROGRESS')
                from branches b order by b.name
                """).query((rs, n) -> new BranchStat(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getLong(10))).list();
    }

    @Transactional(readOnly = true)
    public Totals totals() {
        return jdbc.sql("""
                select
                  (select count(*) from users where active),
                  (select count(*) from users u where active and exists (select 1 from user_roles r where r.user_id = u.id and r.role_code = 'TEACHER')),
                  (select count(*) from users u where active and exists (select 1 from user_roles r where r.user_id = u.id and r.role_code = 'STUDENT')),
                  (select count(*) from users u where active and exists (select 1 from user_roles r where r.user_id = u.id and r.role_code in ('ADMIN','SUPER_ADMIN'))),
                  (select count(*) from branches where active),
                  (select count(*) from exams where status = 'PUBLISHED'),
                  (select count(*) from attempts where status = 'IN_PROGRESS'),
                  (select count(*) from exams where approval_status = 'REJECTED') + (select count(*) from questions where approval_status = 'REJECTED'),
                  (select count(*) from (select 1 from questions where archived = false and content_hash is not null group by subject_id, content_hash, coalesce(image_hash,'') having count(*) > 1) g)
                """).query((rs, n) -> new Totals(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9))).single();
    }
}
