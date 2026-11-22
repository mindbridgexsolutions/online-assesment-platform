package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findAllByOrderByNameAsc();

    List<Subject> findByActiveTrueOrderByNameAsc();

    Optional<Subject> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Query("select s from Subject s join s.teacherIds t where t = :teacherId and s.active = true order by s.name")
    List<Subject> findActiveByTeacher(@Param("teacherId") Long teacherId);

    @Query("select case when count(s) > 0 then true else false end from Subject s join s.teacherIds t "
            + "where t = :teacherId and s.id = :subjectId")
    boolean isTeacherAssigned(@Param("teacherId") Long teacherId, @Param("subjectId") Long subjectId);

    @Query(value = "select s.* from subjects s join users u on u.branch_id = s.branch_id where u.id = :uid and s.active = true order by s.name", nativeQuery = true)
    List<Subject> findActiveInBranchOf(@Param("uid") Long userId);

    @Query(value = "select count(*) > 0 from subjects s join users u on u.branch_id = s.branch_id where u.id = :uid and s.id = :sid", nativeQuery = true)
    boolean isInBranchOf(@Param("uid") Long userId, @Param("sid") Long subjectId);

}
