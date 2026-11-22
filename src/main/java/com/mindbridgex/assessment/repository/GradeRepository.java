package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    Optional<Grade> findByAttemptId(Long attemptId);

    List<Grade> findByAttemptIdIn(Collection<Long> attemptIds);
}
