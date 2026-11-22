package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.entity.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    List<AttemptAnswer> findByAttemptId(Long attemptId);

    List<AttemptAnswer> findByAttemptIdIn(Collection<Long> attemptIds);

    Optional<AttemptAnswer> findByAttemptIdAndAttemptQuestionId(Long attemptId, Long attemptQuestionId);

    /**
     * Idempotent single-row update; returns 0 when the question does not belong to the attempt <b>or when the attempt is
     * no longer in progress</b>, so an answer can never be acknowledged after the attempt has been closed and graded.
     */
    @Modifying(flushAutomatically = true)
    @Query("update AttemptAnswer a set a.answer = :answer, a.flagged = :flagged, a.answeredAt = :now "
            + "where a.attemptId = :attemptId and a.attemptQuestionId = :questionId "
            + "and exists (select 1 from Attempt t where t.id = :attemptId "
            + "and t.status = com.mindbridgex.assessment.domain.AttemptStatus.IN_PROGRESS)")
    int saveAnswer(@Param("attemptId") Long attemptId, @Param("questionId") Long questionId,
                   @Param("answer") String answer, @Param("flagged") boolean flagged, @Param("now") Instant now);

    /** One statement creates the empty answer row of every question of a new attempt (instead of one INSERT per question). */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = "INSERT INTO attempt_answers (attempt_id, attempt_question_id) "
            + "SELECT :attemptId, id FROM attempt_questions WHERE attempt_id = :attemptId", nativeQuery = true)
    int createEmptyAnswers(@Param("attemptId") Long attemptId);
}
