package com.mindbridgex.assessment.entity;

import com.mindbridgex.assessment.domain.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable copy of a question (with its answer key) taken when an attempt starts.
 */
@Entity
@Table(name = "attempt_questions")
public class AttemptQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;
    @Column(nullable = false)
    private int position;
    @Column(name = "source_question_id")
    private Long sourceQuestionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;
    @Column(nullable = false, columnDefinition = "text")
    private String text;
    @Column(name = "image_path", length = 300)
    private String imagePath;
    @Column(columnDefinition = "text")
    private String explanation;
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal marks;
    @Column(name = "negative_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal negativeMarks = BigDecimal.ZERO;
    @Column(name = "partial_marking", nullable = false)
    private boolean partialMarking;
    @Column(name = "case_sensitive", nullable = false)
    private boolean caseSensitive;
    @Column(name = "numeric_answer", precision = 20, scale = 6)
    private BigDecimal numericAnswer;
    @Column(name = "numeric_tolerance", nullable = false, precision = 20, scale = 6)
    private BigDecimal numericTolerance = BigDecimal.ZERO;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", nullable = false, columnDefinition = "jsonb")
    private List<SnapshotOption> options = new ArrayList<>();

    @Column(name = "co_id")
    private Long coId;
    @Column(name = "void_mode", nullable = false, length = 10)
    private String voidMode = "NONE";

    public Long getId() {
        return this.id;
    }

    public Long getAttemptId() {
        return this.attemptId;
    }

    public int getPosition() {
        return this.position;
    }

    public Long getSourceQuestionId() {
        return this.sourceQuestionId;
    }

    public QuestionType getType() {
        return this.type;
    }

    public String getText() {
        return this.text;
    }

    public String getImagePath() {
        return this.imagePath;
    }

    public String getExplanation() {
        return this.explanation;
    }

    public BigDecimal getMarks() {
        return this.marks;
    }

    public BigDecimal getNegativeMarks() {
        return this.negativeMarks;
    }

    public boolean isPartialMarking() {
        return this.partialMarking;
    }

    public boolean isCaseSensitive() {
        return this.caseSensitive;
    }

    public BigDecimal getNumericAnswer() {
        return this.numericAnswer;
    }

    public BigDecimal getNumericTolerance() {
        return this.numericTolerance;
    }

    public List<SnapshotOption> getOptions() {
        return this.options;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setAttemptId(final Long attemptId) {
        this.attemptId = attemptId;
    }

    public void setPosition(final int position) {
        this.position = position;
    }

    public void setSourceQuestionId(final Long sourceQuestionId) {
        this.sourceQuestionId = sourceQuestionId;
    }

    public void setType(final QuestionType type) {
        this.type = type;
    }

    public void setText(final String text) {
        this.text = text;
    }

    public void setImagePath(final String imagePath) {
        this.imagePath = imagePath;
    }

    public void setExplanation(final String explanation) {
        this.explanation = explanation;
    }

    public void setMarks(final BigDecimal marks) {
        this.marks = marks;
    }

    public void setNegativeMarks(final BigDecimal negativeMarks) {
        this.negativeMarks = negativeMarks;
    }

    public void setPartialMarking(final boolean partialMarking) {
        this.partialMarking = partialMarking;
    }

    public void setCaseSensitive(final boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public void setNumericAnswer(final BigDecimal numericAnswer) {
        this.numericAnswer = numericAnswer;
    }

    public void setNumericTolerance(final BigDecimal numericTolerance) {
        this.numericTolerance = numericTolerance;
    }

    public void setOptions(final List<SnapshotOption> options) {
        this.options = options;
    }

    public Long getCoId() {
        return this.coId;
    }

    public void setCoId(final Long coId) {
        this.coId = coId;
    }

    public String getVoidMode() {
        return this.voidMode;
    }

    public void setVoidMode(final String voidMode) {
        this.voidMode = voidMode;
    }
}
