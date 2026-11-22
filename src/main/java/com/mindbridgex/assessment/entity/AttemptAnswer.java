package com.mindbridgex.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "attempt_answers")
public class AttemptAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;
    @Column(name = "attempt_question_id", nullable = false)
    private Long attemptQuestionId;
    /**
     * Raw answer: option key (single), JSON array of keys (multi), JSON object (matching) or free text.
     */
    @Column(columnDefinition = "text")
    private String answer;
    @Column(nullable = false)
    private boolean flagged;
    @Column(name = "answered_at")
    private Instant answeredAt;
    @Column(name = "awarded_marks", precision = 8, scale = 2)
    private BigDecimal awardedMarks;
    private Boolean correct;
    @Column(name = "needs_manual", nullable = false)
    private boolean needsManual;
    @Column(columnDefinition = "text")
    private String feedback;
    @Column(name = "graded_by")
    private Long gradedBy;
    @Column(name = "graded_at")
    private Instant gradedAt;

    public boolean isAnswered() {
        return answer != null && !answer.isBlank() && !"[]".equals(answer.trim()) && !"{}".equals(answer.trim());
    }

    public Long getId() {
        return this.id;
    }

    public Long getAttemptId() {
        return this.attemptId;
    }

    public Long getAttemptQuestionId() {
        return this.attemptQuestionId;
    }

    /**
     * Raw answer: option key (single), JSON array of keys (multi), JSON object (matching) or free text.
     */
    public String getAnswer() {
        return this.answer;
    }

    public boolean isFlagged() {
        return this.flagged;
    }

    public Instant getAnsweredAt() {
        return this.answeredAt;
    }

    public BigDecimal getAwardedMarks() {
        return this.awardedMarks;
    }

    public Boolean getCorrect() {
        return this.correct;
    }

    public boolean isNeedsManual() {
        return this.needsManual;
    }

    public String getFeedback() {
        return this.feedback;
    }

    public Long getGradedBy() {
        return this.gradedBy;
    }

    public Instant getGradedAt() {
        return this.gradedAt;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setAttemptId(final Long attemptId) {
        this.attemptId = attemptId;
    }

    public void setAttemptQuestionId(final Long attemptQuestionId) {
        this.attemptQuestionId = attemptQuestionId;
    }

    /**
     * Raw answer: option key (single), JSON array of keys (multi), JSON object (matching) or free text.
     */
    public void setAnswer(final String answer) {
        this.answer = answer;
    }

    public void setFlagged(final boolean flagged) {
        this.flagged = flagged;
    }

    public void setAnsweredAt(final Instant answeredAt) {
        this.answeredAt = answeredAt;
    }

    public void setAwardedMarks(final BigDecimal awardedMarks) {
        this.awardedMarks = awardedMarks;
    }

    public void setCorrect(final Boolean correct) {
        this.correct = correct;
    }

    public void setNeedsManual(final boolean needsManual) {
        this.needsManual = needsManual;
    }

    public void setFeedback(final String feedback) {
        this.feedback = feedback;
    }

    public void setGradedBy(final Long gradedBy) {
        this.gradedBy = gradedBy;
    }

    public void setGradedAt(final Instant gradedAt) {
        this.gradedAt = gradedAt;
    }
}
