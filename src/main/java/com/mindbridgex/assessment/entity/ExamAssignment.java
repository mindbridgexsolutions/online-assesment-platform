package com.mindbridgex.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Assigns an exam either to a whole batch or to one individual student (exactly one of the two is set).
 */
@Entity
@Table(name = "exam_assignments")
public class ExamAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "exam_id", nullable = false)
    private Long examId;
    @Column(name = "batch_id")
    private Long batchId;
    @Column(name = "student_id")
    private Long studentId;

    public Long getId() {
        return this.id;
    }

    public Long getExamId() {
        return this.examId;
    }

    public Long getBatchId() {
        return this.batchId;
    }

    public Long getStudentId() {
        return this.studentId;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setExamId(final Long examId) {
        this.examId = examId;
    }

    public void setBatchId(final Long batchId) {
        this.batchId = batchId;
    }

    public void setStudentId(final Long studentId) {
        this.studentId = studentId;
    }
}
