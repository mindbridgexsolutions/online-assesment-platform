package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.entity.ExamAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamAssignmentRepository extends JpaRepository<ExamAssignment, Long> {

    List<ExamAssignment> findByExamIdOrderByIdAsc(Long examId);

    Optional<ExamAssignment> findByIdAndExamId(Long id, Long examId);

    boolean existsByExamIdAndBatchId(Long examId, Long batchId);

    boolean existsByExamIdAndStudentId(Long examId, Long studentId);
}
