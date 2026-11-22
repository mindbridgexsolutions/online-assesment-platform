package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findAllByOrderByNameAsc();

    List<Batch> findByActiveTrueOrderByNameAsc();

    Optional<Batch> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Query("select b.id from Batch b join b.studentIds s where s = :studentId")
    List<Long> findBatchIdsOfStudent(@Param("studentId") Long studentId);

    @Query("select b from Batch b join b.studentIds s where s = :studentId order by b.name")
    List<Batch> findBatchesOfStudent(@Param("studentId") Long studentId);
}
