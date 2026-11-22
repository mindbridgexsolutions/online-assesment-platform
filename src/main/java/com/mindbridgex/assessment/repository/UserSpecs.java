package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class UserSpecs {

    private UserSpecs() {
    }

    public static Specification<User> search(RoleType role, String q, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (role != null) {
                ps.add(cb.isMember(role, root.get("roles")));
            }
            if (active != null) {
                ps.add(cb.equal(root.get("active"), active));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like, '\\'),
                        cb.like(cb.lower(root.get("fullName")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("rollNo"), "")), like, '\\')));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
