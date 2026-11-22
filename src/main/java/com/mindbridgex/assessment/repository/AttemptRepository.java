package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.domain.AttemptStatus;
import com.mindbridgex.assessment.dto.ExamResultRow;
import com.mindbridgex.assessment.dto.PendingGradingRow;
import com.mindbridgex.assessment.dto.StudentAttemptRow;
import com.mindbridgex.assessment.entity.Attempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    Optional<Attempt> findByIdAndStudentId(Long id, Long studentId);

    Optional<Attempt> findFirstByExamIdAndStudentIdAndStatus(Long examId, Long studentId, AttemptStatus status);

    long countByExamIdAndStudentId(Long examId, Long studentId);

    long countByExamIdAndStatus(Long examId, AttemptStatus status);

    long countByStatus(AttemptStatus status);

    long countByExamId(Long examId);

    List<Attempt> findByExamId(Long examId);

    @Query("select max(a.attemptNo) from Attempt a where a.examId = :examId and a.studentId = :studentId")
    Integer maxAttemptNo(@Param("examId") Long examId, @Param("studentId") Long studentId);

    @Query("select a.id from Attempt a where a.status = com.mindbridgex.assessment.domain.AttemptStatus.IN_PROGRESS "
            + "and a.endsAt < :cutoff order by a.endsAt asc")
    List<Long> findExpiredIds(@Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Reads the attempt status and takes a shared row lock until the transaction ends. A concurrent submit (which
     * UPDATEs this row) waits for in-flight answer saves, and a save that starts after the submit sees the new status,
     * so no acknowledged answer can miss the grading. Returns null when the attempt does not exist.
     */
    @Query(value = "SELECT status FROM attempts WHERE id = :id FOR SHARE", nativeQuery = true)
    String lockStatusShared(@Param("id") Long id);

    /** Submitted attempts that have no grade row (grading failed once); they are retried by the scheduler. */
    @Query("select a.id from Attempt a where a.status = com.mindbridgex.assessment.domain.AttemptStatus.SUBMITTED "
            + "and not exists (select 1 from Grade g where g.attemptId = a.id) order by a.submittedAt asc")
    List<Long> findSubmittedWithoutGrade(Pageable pageable);

    @Query("select a.id from Attempt a where a.examId = :examId and a.status = com.mindbridgex.assessment.domain.AttemptStatus.IN_PROGRESS")
    List<Long> findActiveIdsByExam(@Param("examId") Long examId);

    /** Winner-takes-all transition: only one caller (student submit or scheduler) can flip IN_PROGRESS -> SUBMITTED. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE attempts SET status = 'SUBMITTED', submitted_at = :now, submit_reason = :reason, "
            + "version = version + 1, updated_at = :now WHERE id = :id AND status = 'IN_PROGRESS'", nativeQuery = true)
    int markSubmitted(@Param("id") Long id, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE attempts SET player_session = :session, version = version + 1, updated_at = now() "
            + "WHERE id = :id AND status = 'IN_PROGRESS'", nativeQuery = true)
    int rotatePlayerSession(@Param("id") Long id, @Param("session") String session);

    @Query(value = "UPDATE attempts SET focus_loss_count = focus_loss_count + 1, version = version + 1, updated_at = now() "
            + "WHERE id = :id AND status = 'IN_PROGRESS' RETURNING focus_loss_count", nativeQuery = true)
    Integer incrementFocusLoss(@Param("id") Long id);

    @Query("""
            select new com.mindbridgex.assessment.dto.ExamResultRow(a.id, u.id, u.fullName, u.rollNo, a.attemptNo, a.status,
                   a.submittedAt, g.totalScore, g.maxScore, g.percentage, g.passed, g.pendingManual, a.focusLossCount)
            from Attempt a
              join User u on u.id = a.studentId
              left join Grade g on g.attemptId = a.id
            where a.examId = :examId
            order by u.fullName asc, a.attemptNo asc
            """)
    List<ExamResultRow> findResultRows(@Param("examId") Long examId);

    @Query("""
            select new com.mindbridgex.assessment.dto.StudentAttemptRow(a.id, e.id, e.title, a.attemptNo, a.status,
                   a.startedAt, a.submittedAt, g.totalScore, g.maxScore, g.percentage, g.passed, g.pendingManual,
                   e.resultPolicy, e.endAt, e.resultsReleased, e.reviewAllowed, e.resultsPublishAt)
            from Attempt a
              join Exam e on e.id = a.examId
              left join Grade g on g.attemptId = a.id
            where a.studentId = :studentId
            order by a.startedAt desc
            """)
    List<StudentAttemptRow> findStudentRows(@Param("studentId") Long studentId);

    @Query("""
            select new com.mindbridgex.assessment.dto.PendingGradingRow(a.id, e.id, e.title, u.fullName, u.rollNo,
                   a.submittedAt, g.pendingManual)
            from Attempt a
              join Exam e on e.id = a.examId
              join User u on u.id = a.studentId
              join Grade g on g.attemptId = a.id
            where e.subject.id in (select sb.id from Subject sb join sb.teacherIds tt where tt = :teacherId) and g.pendingManual > 0
              and a.status = com.mindbridgex.assessment.domain.AttemptStatus.SUBMITTED
            order by a.submittedAt asc
            """)
    List<PendingGradingRow> findPendingGradingForTeacher(@Param("teacherId") Long teacherId);

    @Query("""
            select count(a) from Attempt a join Exam e on e.id = a.examId
            join Grade g on g.attemptId = a.id
            where e.subject.id in (select sb.id from Subject sb join sb.teacherIds tt where tt = :teacherId) and g.pendingManual > 0
              and a.status = com.mindbridgex.assessment.domain.AttemptStatus.SUBMITTED
            """)
    long countPendingGradingForTeacher(@Param("teacherId") Long teacherId);
}
