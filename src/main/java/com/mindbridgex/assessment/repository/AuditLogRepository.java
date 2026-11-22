package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    @org.springframework.data.jpa.repository.Query("select distinct a.action from AuditLog a order by a.action")
    List<String> distinctActions();

    static Specification<AuditLog> filter(String action, String actor, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (action != null && !action.isBlank()) {
                ps.add(cb.equal(root.get("action"), action.trim()));
            }
            if (actor != null && !actor.isBlank()) {
                String like = "%" + actor.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.like(cb.lower(cb.coalesce(root.get("actorName"), "")), like, '\\'));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                ps.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
