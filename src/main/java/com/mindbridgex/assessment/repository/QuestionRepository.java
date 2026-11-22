package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    List<Question> findByIdIn(Collection<Long> ids);

    @Query("select count(q) from Question q where q.category.id = :categoryId and q.archived = false")
    long countActiveInCategory(@Param("categoryId") Long categoryId);

    @org.springframework.data.jpa.repository.Query("select q.id from Question q where q.subject.id = :sid and q.archived = false and q.contentHash = :h "
            + "and q.imageHash = :ih and (:ex = 0L or q.id <> :ex) order by q.id")
    List<Long> findDuplicatesWithImage(@org.springframework.data.repository.query.Param("sid") Long subjectId, @org.springframework.data.repository.query.Param("h") String hash,
            @org.springframework.data.repository.query.Param("ih") String imageHash, @org.springframework.data.repository.query.Param("ex") Long excludeId);

    @org.springframework.data.jpa.repository.Query("select q.id from Question q where q.subject.id = :sid and q.archived = false and q.contentHash = :h "
            + "and q.imageHash is null and (:ex = 0L or q.id <> :ex) order by q.id")
    List<Long> findDuplicatesNoImage(@org.springframework.data.repository.query.Param("sid") Long subjectId, @org.springframework.data.repository.query.Param("h") String hash,
            @org.springframework.data.repository.query.Param("ex") Long excludeId);

    default List<Long> findDuplicates(Long subjectId, String hash, String imageHash, Long excludeId) {
        long ex = excludeId == null ? 0L : excludeId;
        return imageHash == null ? findDuplicatesNoImage(subjectId, hash, ex) : findDuplicatesWithImage(subjectId, hash, imageHash, ex);
    }

    @org.springframework.data.jpa.repository.Query("select q from Question q where q.contentHash is null order by q.id")
    List<Question> findUnhashed(org.springframework.data.domain.Pageable pageable);
}
