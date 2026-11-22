package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.entity.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {

    List<ExamQuestion> findByExamIdOrderByPositionAscIdAsc(Long examId);

    Optional<ExamQuestion> findByIdAndExamId(Long id, Long examId);

    boolean existsByExamIdAndQuestionId(Long examId, Long questionId);

    boolean existsByQuestionId(Long questionId);

    @Query("select coalesce(max(eq.position), 0) from ExamQuestion eq where eq.examId = :examId")
    int maxPosition(@Param("examId") Long examId);

    @Query("select coalesce(sum(eq.marks), 0) from ExamQuestion eq where eq.examId = :examId")
    BigDecimal sumMarks(@Param("examId") Long examId);

    long countByExamId(Long examId);
}
