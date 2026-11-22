package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.domain.Difficulty;
import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.entity.Question;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class QuestionSpecs {

    private QuestionSpecs() {
    }

    public static Specification<Question> search(Collection<Long> subjectIds, Long subjectId, Long categoryId,
                                                 QuestionType type, Difficulty difficulty, String tag, String q) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("archived")));
            if (subjectIds != null) {
                if (subjectIds.isEmpty()) {
                    return cb.disjunction();
                }
                ps.add(root.get("subject").get("id").in(subjectIds));
            }
            if (subjectId != null) {
                ps.add(cb.equal(root.get("subject").get("id"), subjectId));
            }
            if (categoryId != null) {
                ps.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (type != null) {
                ps.add(cb.equal(root.get("type"), type));
            }
            if (difficulty != null) {
                ps.add(cb.equal(root.get("difficulty"), difficulty));
            }
            if (tag != null && !tag.isBlank()) {
                ps.add(cb.isMember(tag.trim().toLowerCase(), root.<java.util.Set<String>>get("tags")));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.like(cb.lower(root.get("text")), like, '\\'));
            }
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(cb.desc(root.get("id")));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
