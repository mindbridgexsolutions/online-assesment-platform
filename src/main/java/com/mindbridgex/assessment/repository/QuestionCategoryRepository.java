package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.entity.QuestionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface QuestionCategoryRepository extends JpaRepository<QuestionCategory, Long> {

    List<QuestionCategory> findBySubjectIdInOrderBySubjectNameAscNameAsc(Collection<Long> subjectIds);

    List<QuestionCategory> findBySubjectIdOrderByNameAsc(Long subjectId);

    boolean existsBySubjectIdAndNameIgnoreCase(Long subjectId, String name);

    java.util.Optional<QuestionCategory> findBySubjectIdAndNameIgnoreCase(Long subjectId, String name);
}
