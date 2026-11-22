package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.domain.ExamStatus;
import com.mindbridgex.assessment.entity.Exam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    Page<Exam> findByCreatedByOrderByStartAtDesc(Long createdBy, Pageable pageable);

    Page<Exam> findAllByOrderByStartAtDesc(Pageable pageable);

    Page<Exam> findBySubjectIdInOrderByStartAtDesc(java.util.Collection<Long> subjectIds, Pageable pageable);

    long countBySubjectIdInAndStatus(java.util.Collection<Long> subjectIds, ExamStatus status);

    long countByCreatedByAndStatus(Long createdBy, ExamStatus status);

    long countByStatus(ExamStatus status);

    @Query("""
            select e from Exam e
            where e.status = com.mindbridgex.assessment.domain.ExamStatus.PUBLISHED
              and (exists (select 1 from ExamAssignment a where a.examId = e.id and a.studentId = :sid)
                or exists (select 1 from ExamAssignment a where a.examId = e.id and a.batchId in
                           (select b.id from Batch b join b.studentIds s where s = :sid)))
            order by e.startAt desc
            """)
    List<Exam> findVisibleToStudent(@Param("sid") Long studentId);

    @Query("""
            select case when count(e) > 0 then true else false end from Exam e
            where e.id = :examId
              and (exists (select 1 from ExamAssignment a where a.examId = e.id and a.studentId = :sid)
                or exists (select 1 from ExamAssignment a where a.examId = e.id and a.batchId in
                           (select b.id from Batch b join b.studentIds s where s = :sid)))
            """)
    boolean isAssignedToStudent(@Param("examId") Long examId, @Param("sid") Long studentId);

    @Query("""
            select e from Exam e
            where e.status = com.mindbridgex.assessment.domain.ExamStatus.PUBLISHED
              and e.resultPolicy = com.mindbridgex.assessment.domain.ResultPolicy.AFTER_WINDOW
              and e.endAt < :now
            """)
    List<Exam> findWindowClosedExams(@Param("now") java.time.Instant now);
}
