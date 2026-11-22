package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.entity.Question;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.QuestionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

/** Finds groups of identical questions and merges them without breaking exams that already used them. */
@Service
@Order(Integer.MAX_VALUE)
public class DuplicateService implements ApplicationRunner {
    private final JdbcClient jdbc;
    private final QuestionRepository questions;
    private final QuestionHasher hasher;
    private final AuditService audit;

    public record Member(Long id, String text, String category, String source, long exams, long attempts, String createdBy) {
    }

    public record Group(String subject, String key, List<Member> members) {
    }

    public DuplicateService(JdbcClient jdbc, QuestionRepository questions, QuestionHasher hasher, AuditService audit) {
        this.jdbc = jdbc;
        this.questions = questions;
        this.hasher = hasher;
        this.audit = audit;
    }

    /** Questions created before duplicate detection existed get their fingerprints once, at start-up. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Question> batch;
        while (!(batch = questions.findUnhashed(PageRequest.of(0, 200))).isEmpty()) {
            for (Question q : batch) {
                QuestionHasher.Hashes h = hasher.compute(q);
                q.setContentHash(h.content());
                q.setImageHash(h.image());
            }
            questions.saveAll(batch);
            questions.flush();
        }
    }

    @Transactional(readOnly = true)
    public List<Group> groups() {
        List<Object[]> keys = new ArrayList<>();
        jdbc.sql("""
                select s.name, q.subject_id, q.content_hash, coalesce(q.image_hash,'')
                from questions q join subjects s on s.id = q.subject_id
                where q.archived = false and q.content_hash is not null
                group by s.name, q.subject_id, q.content_hash, coalesce(q.image_hash,'')
                having count(*) > 1 order by s.name, min(q.id)
                """).query((rs, n) -> {
            keys.add(new Object[] {rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4)});
            return 0;
        }).list();
        List<Group> out = new ArrayList<>();
        for (Object[] k : keys) {
            List<Member> members = jdbc.sql("""
                    select q.id, q.text, coalesce(c.name,''), coalesce(q.source,''),
                      (select count(*) from exam_questions eq where eq.question_id = q.id),
                      (select count(*) from attempt_questions aq where aq.source_question_id = q.id),
                      coalesce(u.full_name,'')
                    from questions q left join question_categories c on c.id = q.category_id left join users u on u.id = q.created_by
                    where q.archived = false and q.subject_id = :s and q.content_hash = :h and coalesce(q.image_hash,'') = :i order by q.id
                    """).param("s", k[1]).param("h", k[2]).param("i", k[3])
                    .query((rs, n) -> new Member(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getLong(6), rs.getString(7))).list();
            out.add(new Group((String) k[0], ((String) k[2]).substring(0, 8), members));
        }
        return out;
    }

    /**
     * Keeps one question and archives the others. Exams and past attempts that pointed at a removed copy are
     * re-pointed at the kept one (or, if the exam already contains the kept question, the extra row is dropped),
     * so no exam, attempt or score changes.
     */
    @Transactional
    public int merge(Long keepId, List<Long> removeIds) {
        if (removeIds == null) {
            removeIds = List.of();
        }
        List<Long> remove = removeIds.stream().filter(id -> !id.equals(keepId)).distinct().toList();
        if (remove.isEmpty()) {
            throw AppException.bad("Tick at least one copy to remove.");
        }
        Question keep = questions.findById(keepId).orElseThrow(() -> AppException.notFound("Question"));
        for (Long id : remove) {
            Question dup = questions.findById(id).orElseThrow(() -> AppException.notFound("Question"));
            if (!dup.getSubject().getId().equals(keep.getSubject().getId())) {
                throw AppException.bad("Only questions of the same subject can be merged.");
            }
            jdbc.sql("delete from exam_questions where question_id = :d and exam_id in (select exam_id from exam_questions where question_id = :k)")
                    .param("d", id).param("k", keepId).update();
            jdbc.sql("update exam_questions set question_id = :k where question_id = :d").param("k", keepId).param("d", id).update();
            jdbc.sql("update attempt_questions set source_question_id = :k where source_question_id = :d").param("k", keepId).param("d", id).update();
            dup.setArchived(true);
            questions.save(dup);
            audit.log("QUESTION_MERGE", "Question", id, "merged into question " + keepId);
        }
        return remove.size();
    }
}
