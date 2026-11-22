package com.mindbridgex.assessment.entity;

import com.mindbridgex.assessment.domain.Difficulty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * "Pick N random questions of difficulty D from category C" rule, resolved per student at attempt start.
 */
@Entity
@Table(name = "exam_question_rules")
public class ExamQuestionRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "exam_id", nullable = false)
    private Long examId;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private QuestionCategory category;
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Difficulty difficulty;
    @Column(name = "question_count", nullable = false)
    private int questionCount;
    @Column(name = "marks_per_question", precision = 6, scale = 2)
    private BigDecimal marksPerQuestion;

    public Long getId() {
        return this.id;
    }

    public Long getExamId() {
        return this.examId;
    }

    public QuestionCategory getCategory() {
        return this.category;
    }

    public Difficulty getDifficulty() {
        return this.difficulty;
    }

    public int getQuestionCount() {
        return this.questionCount;
    }

    public BigDecimal getMarksPerQuestion() {
        return this.marksPerQuestion;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setExamId(final Long examId) {
        this.examId = examId;
    }

    public void setCategory(final QuestionCategory category) {
        this.category = category;
    }

    public void setDifficulty(final Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public void setQuestionCount(final int questionCount) {
        this.questionCount = questionCount;
    }

    public void setMarksPerQuestion(final BigDecimal marksPerQuestion) {
        this.marksPerQuestion = marksPerQuestion;
    }
}
