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
@Table(name = "grades")
public class Grade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "attempt_id", nullable = false, unique = true)
    private Long attemptId;
    @Column(name = "total_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalScore = BigDecimal.ZERO;
    @Column(name = "max_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxScore = BigDecimal.ZERO;
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal percentage = BigDecimal.ZERO;
    @Column(nullable = false)
    private boolean passed;
    @Column(name = "pending_manual", nullable = false)
    private int pendingManual;
    @Column(name = "graded_at", nullable = false)
    private Instant gradedAt = Instant.now();

    public Long getId() {
        return this.id;
    }

    public Long getAttemptId() {
        return this.attemptId;
    }

    public BigDecimal getTotalScore() {
        return this.totalScore;
    }

    public BigDecimal getMaxScore() {
        return this.maxScore;
    }

    public BigDecimal getPercentage() {
        return this.percentage;
    }

    public boolean isPassed() {
        return this.passed;
    }

    public int getPendingManual() {
        return this.pendingManual;
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

    public void setTotalScore(final BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public void setMaxScore(final BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public void setPercentage(final BigDecimal percentage) {
        this.percentage = percentage;
    }

    public void setPassed(final boolean passed) {
        this.passed = passed;
    }

    public void setPendingManual(final int pendingManual) {
        this.pendingManual = pendingManual;
    }

    public void setGradedAt(final Instant gradedAt) {
        this.gradedAt = gradedAt;
    }
}
