package com.mindbridgex.assessment.entity;

import com.mindbridgex.assessment.domain.ExamStatus;
import com.mindbridgex.assessment.domain.ResultPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "exams")
public class Exam extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "text")
    private String instructions;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 60;
    @Column(name = "start_at", nullable = false)
    private Instant startAt;
    @Column(name = "end_at", nullable = false)
    private Instant endAt;
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 1;
    @Column(name = "passing_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal passingMarks = BigDecimal.ZERO;
    @Column(name = "negative_marking", nullable = false)
    private boolean negativeMarking;
    @Column(name = "negative_fraction", nullable = false, precision = 4, scale = 2)
    private BigDecimal negativeFraction = new BigDecimal("0.25");
    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions = true;
    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions = true;
    @Enumerated(EnumType.STRING)
    @Column(name = "result_policy", nullable = false, length = 20)
    private ResultPolicy resultPolicy = ResultPolicy.AFTER_WINDOW;
    @Column(name = "results_released", nullable = false)
    private boolean resultsReleased;
    @Column(name = "review_allowed", nullable = false)
    private boolean reviewAllowed = true;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ExamStatus status = ExamStatus.DRAFT;
    @Column(name = "allowed_ips", columnDefinition = "text")
    private String allowedIps;
    @Column(name = "max_focus_losses", nullable = false)
    private int maxFocusLosses;
    @Column(name = "block_copy_paste", nullable = false)
    private boolean blockCopyPaste = true;

    @Column(name = "results_publish_at")
    private Instant resultsPublishAt;
    @Column(name = "approval_status", nullable = false, length = 10)
    private String approvalStatus = "APPROVED";

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 20)
    private com.mindbridgex.assessment.domain.ExamType examType = com.mindbridgex.assessment.domain.ExamType.INTERNAL;
    @Column(name = "result_delay_days")
    private Integer resultDelayDays;

    public boolean isPublished() {
        return status == ExamStatus.PUBLISHED;
    }

    public boolean isOpenAt(Instant now) {
        return isPublished() && !now.isBefore(startAt) && now.isBefore(endAt);
    }

    public Subject getSubject() {
        return this.subject;
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

    public Instant getStartAt() {
        return this.startAt;
    }

    public Instant getEndAt() {
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

    public boolean isResultsReleased() {
        return this.resultsReleased;
    }

    public boolean isReviewAllowed() {
        return this.reviewAllowed;
    }

    public ExamStatus getStatus() {
        return this.status;
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

    public void setSubject(final Subject subject) {
        this.subject = subject;
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

    public void setStartAt(final Instant startAt) {
        this.startAt = startAt;
    }

    public void setEndAt(final Instant endAt) {
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

    public void setResultsReleased(final boolean resultsReleased) {
        this.resultsReleased = resultsReleased;
    }

    public void setReviewAllowed(final boolean reviewAllowed) {
        this.reviewAllowed = reviewAllowed;
    }

    public void setStatus(final ExamStatus status) {
        this.status = status;
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

    public Instant getResultsPublishAt() {
        return this.resultsPublishAt;
    }

    public void setResultsPublishAt(final Instant resultsPublishAt) {
        this.resultsPublishAt = resultsPublishAt;
    }

    public String getApprovalStatus() {
        return this.approvalStatus;
    }

    public void setApprovalStatus(final String approvalStatus) {
        this.approvalStatus = approvalStatus;
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
