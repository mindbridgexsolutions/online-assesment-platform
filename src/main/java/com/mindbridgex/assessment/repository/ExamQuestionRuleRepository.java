package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.entity.ExamQuestionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamQuestionRuleRepository extends JpaRepository<ExamQuestionRule, Long> {

    List<ExamQuestionRule> findByExamIdOrderByIdAsc(Long examId);

    Optional<ExamQuestionRule> findByIdAndExamId(Long id, Long examId);
}
