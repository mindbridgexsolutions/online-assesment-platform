package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.dto.QuestionStat;
import com.mindbridgex.assessment.entity.AttemptQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttemptQuestionRepository extends JpaRepository<AttemptQuestion, Long> {

    List<AttemptQuestion> findByAttemptIdOrderByPositionAsc(Long attemptId);

    Optional<AttemptQuestion> findByIdAndAttemptId(Long id, Long attemptId);

    @Query("select aq from AttemptQuestion aq where aq.sourceQuestionId = :questionId "
            + "and aq.attemptId in (select a.id from Attempt a where a.examId = :examId)")
    List<AttemptQuestion> findForExamQuestion(@Param("examId") Long examId, @Param("questionId") Long questionId);

    @Query("""
            select new com.mindbridgex.assessment.dto.QuestionStat(aq.sourceQuestionId, aq.type, min(aq.text),
                   count(aa.id),
                   count(case when aa.answer is not null and aa.answer <> '' then 1 end),
                   count(case when aa.correct = true then 1 end),
                   avg(aa.awardedMarks), max(aq.marks))
            from AttemptAnswer aa
              join AttemptQuestion aq on aq.id = aa.attemptQuestionId
              join Attempt a on a.id = aa.attemptId
            where a.examId = :examId and a.status = com.mindbridgex.assessment.domain.AttemptStatus.GRADED
            group by aq.sourceQuestionId, aq.type
            order by min(aq.id)
            """)
    List<QuestionStat> questionStats(@Param("examId") Long examId);
}
