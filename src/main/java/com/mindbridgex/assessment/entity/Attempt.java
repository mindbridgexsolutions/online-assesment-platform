package com.mindbridgex.assessment.entity;

import com.mindbridgex.assessment.domain.AttemptStatus;
import com.mindbridgex.assessment.domain.SubmitReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "attempts")
public class Attempt extends BaseEntity {
    @Column(name = "exam_id", nullable = false)
    private Long examId;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "submit_reason", length = 12)
    private SubmitReason submitReason;
    @Column(name = "player_session", length = 64)
    private String playerSession;
    @Column(name = "focus_loss_count", nullable = false)
    private int focusLossCount;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "user_agent", length = 255)
    private String userAgent;
    @Version
    @Column(nullable = false)
    private long version;

    public boolean isInProgress() {
        return status == AttemptStatus.IN_PROGRESS;
    }

    public Long getExamId() {
        return this.examId;
    }

    public Long getStudentId() {
        return this.studentId;
    }

    public int getAttemptNo() {
        return this.attemptNo;
    }

    public AttemptStatus getStatus() {
        return this.status;
    }

    public Instant getStartedAt() {
        return this.startedAt;
    }

    public Instant getEndsAt() {
        return this.endsAt;
    }

    public Instant getSubmittedAt() {
        return this.submittedAt;
    }

    public SubmitReason getSubmitReason() {
        return this.submitReason;
    }

    public String getPlayerSession() {
        return this.playerSession;
    }

    public int getFocusLossCount() {
        return this.focusLossCount;
    }

    public String getIpAddress() {
        return this.ipAddress;
    }

    public String getUserAgent() {
        return this.userAgent;
    }

    public long getVersion() {
        return this.version;
    }

    public void setExamId(final Long examId) {
        this.examId = examId;
    }

    public void setStudentId(final Long studentId) {
        this.studentId = studentId;
    }

    public void setAttemptNo(final int attemptNo) {
        this.attemptNo = attemptNo;
    }

    public void setStatus(final AttemptStatus status) {
        this.status = status;
    }

    public void setStartedAt(final Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void setEndsAt(final Instant endsAt) {
        this.endsAt = endsAt;
    }

    public void setSubmittedAt(final Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public void setSubmitReason(final SubmitReason submitReason) {
        this.submitReason = submitReason;
    }

    public void setPlayerSession(final String playerSession) {
        this.playerSession = playerSession;
    }

    public void setFocusLossCount(final int focusLossCount) {
        this.focusLossCount = focusLossCount;
    }

    public void setIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
    }

    public void setVersion(final long version) {
        this.version = version;
    }
}
