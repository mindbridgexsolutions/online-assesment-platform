package com.mindbridgex.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A question picked manually for an exam.
 */
@Entity
@Table(name = "exam_questions")
public class ExamQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "exam_id", nullable = false)
    private Long examId;
    @Column(name = "question_id", nullable = false)
    private Long questionId;
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal marks;
    @Column(nullable = false)
    private int position;

    @Column(name = "void_mode", nullable = false, length = 10)
    private String voidMode = "NONE";

    public Long getId() {
        return this.id;
    }

    public Long getExamId() {
        return this.examId;
    }

    public Long getQuestionId() {
        return this.questionId;
    }

    public BigDecimal getMarks() {
        return this.marks;
    }

    public int getPosition() {
        return this.position;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setExamId(final Long examId) {
        this.examId = examId;
    }

    public void setQuestionId(final Long questionId) {
        this.questionId = questionId;
    }

    public void setMarks(final BigDecimal marks) {
        this.marks = marks;
    }

    public void setPosition(final int position) {
        this.position = position;
    }

    public String getVoidMode() {
        return this.voidMode;
    }

    public void setVoidMode(final String voidMode) {
        this.voidMode = voidMode;
    }
}
