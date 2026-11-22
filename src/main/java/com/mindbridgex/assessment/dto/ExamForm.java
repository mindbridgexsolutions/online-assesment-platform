package com.mindbridgex.assessment.dto;

import com.mindbridgex.assessment.domain.ResultPolicy;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ExamForm {
    private Long id;
    @NotNull(message = "Select a subject")
    private Long subjectId;
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title is too long (max 200 characters)")
    private String title;
    @Size(max = 5000, message = "Instructions are too long")
    private String instructions;
    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Max(value = 600, message = "Duration cannot exceed 600 minutes")
    private int durationMinutes = 60;
    @NotNull(message = "Start time is required")
    @DateTimeFormat(pattern = "yyyy-MM-dd\'T\'HH:mm")
    private LocalDateTime startAt;
    @NotNull(message = "End time is required")
    @DateTimeFormat(pattern = "yyyy-MM-dd\'T\'HH:mm")
    private LocalDateTime endAt;
    @Min(value = 1, message = "Allow at least one attempt")
    @Max(value = 20, message = "At most 20 attempts")
    private int maxAttempts = 1;
    @NotNull(message = "Passing marks are required")
    @DecimalMin(value = "0", message = "Passing marks cannot be negative")
    private BigDecimal passingMarks = BigDecimal.ZERO;
    private boolean negativeMarking;
    @DecimalMin(value = "0", message = "Negative fraction must be between 0 and 1")
    @DecimalMax(value = "1", message = "Negative fraction must be between 0 and 1")
    private BigDecimal negativeFraction = new BigDecimal("0.25");
    private boolean shuffleQuestions = true;
    private boolean shuffleOptions = true;
    @NotNull
    private ResultPolicy resultPolicy = ResultPolicy.AFTER_WINDOW;
    private boolean reviewAllowed = true;
    @Size(max = 2000, message = "Allowed IP list is too long")
    private String allowedIps;
    @Min(value = 0, message = "Focus-loss limit cannot be negative")
    @Max(value = 50, message = "Focus-loss limit is too high")
    private int maxFocusLosses;
    private boolean blockCopyPaste = true;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private java.time.LocalDateTime resultsPublishAt;

    private com.mindbridgex.assessment.domain.ExamType examType = com.mindbridgex.assessment.domain.ExamType.INTERNAL;
    private Integer resultDelayDays;

    public Long getId() {
        return this.id;
    }

    public Long getSubjectId() {
        return this.subjectId;
    }

    public String getTitle() {
        return this.title;
    }

    public String getInstructions() {
        return this.instructions;
    }

    public int getDurationMinutes() {
        return this.durationMinutes;
    }

    public LocalDateTime getStartAt() {
        return this.startAt;
    }

    public LocalDateTime getEndAt() {
        return this.endAt;
    }

    public int getMaxAttempts() {
        return this.maxAttempts;
    }

    public BigDecimal getPassingMarks() {
        return this.passingMarks;
    }

    public boolean isNegativeMarking() {
        return this.negativeMarking;
    }

    public BigDecimal getNegativeFraction() {
        return this.negativeFraction;
    }

    public boolean isShuffleQuestions() {
        return this.shuffleQuestions;
    }

    public boolean isShuffleOptions() {
        return this.shuffleOptions;
    }

    public ResultPolicy getResultPolicy() {
        return this.resultPolicy;
    }

    public boolean isReviewAllowed() {
        return this.reviewAllowed;
    }

    public String getAllowedIps() {
        return this.allowedIps;
    }

    public int getMaxFocusLosses() {
        return this.maxFocusLosses;
    }

    public boolean isBlockCopyPaste() {
        return this.blockCopyPaste;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setSubjectId(final Long subjectId) {
        this.subjectId = subjectId;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public void setInstructions(final String instructions) {
        this.instructions = instructions;
    }

    public void setDurationMinutes(final int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public void setStartAt(final LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public void setEndAt(final LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public void setMaxAttempts(final int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void setPassingMarks(final BigDecimal passingMarks) {
        this.passingMarks = passingMarks;
    }

    public void setNegativeMarking(final boolean negativeMarking) {
        this.negativeMarking = negativeMarking;
    }

    public void setNegativeFraction(final BigDecimal negativeFraction) {
        this.negativeFraction = negativeFraction;
    }

    public void setShuffleQuestions(final boolean shuffleQuestions) {
        this.shuffleQuestions = shuffleQuestions;
    }

    public void setShuffleOptions(final boolean shuffleOptions) {
        this.shuffleOptions = shuffleOptions;
    }

    public void setResultPolicy(final ResultPolicy resultPolicy) {
        this.resultPolicy = resultPolicy;
    }

    public void setReviewAllowed(final boolean reviewAllowed) {
        this.reviewAllowed = reviewAllowed;
    }

    public void setAllowedIps(final String allowedIps) {
        this.allowedIps = allowedIps;
    }

    public void setMaxFocusLosses(final int maxFocusLosses) {
        this.maxFocusLosses = maxFocusLosses;
    }

    public void setBlockCopyPaste(final boolean blockCopyPaste) {
        this.blockCopyPaste = blockCopyPaste;
    }

    public java.time.LocalDateTime getResultsPublishAt() {
        return this.resultsPublishAt;
    }

    public void setResultsPublishAt(final java.time.LocalDateTime resultsPublishAt) {
        this.resultsPublishAt = resultsPublishAt;
    }

    public com.mindbridgex.assessment.domain.ExamType getExamType() {
        return this.examType;
    }

    public void setExamType(final com.mindbridgex.assessment.domain.ExamType examType) {
        this.examType = examType;
    }

    public Integer getResultDelayDays() {
        return this.resultDelayDays;
    }

    public void setResultDelayDays(final Integer resultDelayDays) {
        this.resultDelayDays = resultDelayDays;
    }
}
