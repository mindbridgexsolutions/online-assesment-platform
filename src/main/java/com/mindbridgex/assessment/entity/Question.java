package com.mindbridgex.assessment.entity;

import com.mindbridgex.assessment.domain.Difficulty;
import com.mindbridgex.assessment.domain.QuestionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "questions")
public class Question extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private QuestionCategory category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty = Difficulty.MEDIUM;
    @Column(nullable = false, columnDefinition = "text")
    private String text;
    @Column(name = "image_path", length = 300)
    private String imagePath;
    @Column(columnDefinition = "text")
    private String explanation;
    @Column(name = "default_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal defaultMarks = BigDecimal.ONE;
    @Column(name = "partial_marking", nullable = false)
    private boolean partialMarking;
    @Column(name = "case_sensitive", nullable = false)
    private boolean caseSensitive;
    @Column(name = "numeric_answer", precision = 20, scale = 6)
    private BigDecimal numericAnswer;
    @Column(name = "numeric_tolerance", nullable = false, precision = 20, scale = 6)
    private BigDecimal numericTolerance = BigDecimal.ZERO;
    @Column(nullable = false)
    private boolean archived;
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    @BatchSize(size = 100)
    private List<QuestionOption> options = new ArrayList<>();
    @ElementCollection
    @CollectionTable(name = "question_tags", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "tag", length = 40)
    @BatchSize(size = 100)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "content_hash", length = 64)
    private String contentHash;
    @Column(name = "image_hash", length = 64)
    private String imageHash;
    @Column(length = 250)
    private String source;
    @Column(name = "co_id")
    private Long coId;
    @Column(name = "approval_status", nullable = false, length = 10)
    private String approvalStatus = "APPROVED";

    @Enumerated(EnumType.STRING)
    @Column(name = "bloom", length = 12)
    private com.mindbridgex.assessment.domain.Bloom bloom;
    @Column(name = "unit", length = 80)
    private String unit;

    public void replaceTags(Set<String> newTags) {
        tags.clear();
        tags.addAll(newTags);
    }

    public Subject getSubject() {
        return this.subject;
    }

    public QuestionCategory getCategory() {
        return this.category;
    }

    public QuestionType getType() {
        return this.type;
    }

    public Difficulty getDifficulty() {
        return this.difficulty;
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

    public BigDecimal getDefaultMarks() {
        return this.defaultMarks;
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

    public boolean isArchived() {
        return this.archived;
    }

    public List<QuestionOption> getOptions() {
        return this.options;
    }

    public Set<String> getTags() {
        return this.tags;
    }

    public void setSubject(final Subject subject) {
        this.subject = subject;
    }

    public void setCategory(final QuestionCategory category) {
        this.category = category;
    }

    public void setType(final QuestionType type) {
        this.type = type;
    }

    public void setDifficulty(final Difficulty difficulty) {
        this.difficulty = difficulty;
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

    public void setDefaultMarks(final BigDecimal defaultMarks) {
        this.defaultMarks = defaultMarks;
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

    public void setArchived(final boolean archived) {
        this.archived = archived;
    }

    public void setOptions(final List<QuestionOption> options) {
        this.options = options;
    }

    public void setTags(final Set<String> tags) {
        this.tags = tags;
    }

    public String getContentHash() {
        return this.contentHash;
    }

    public void setContentHash(final String contentHash) {
        this.contentHash = contentHash;
    }

    public String getImageHash() {
        return this.imageHash;
    }

    public void setImageHash(final String imageHash) {
        this.imageHash = imageHash;
    }

    public String getSource() {
        return this.source;
    }

    public void setSource(final String source) {
        this.source = source;
    }

    public Long getCoId() {
        return this.coId;
    }

    public void setCoId(final Long coId) {
        this.coId = coId;
    }

    public String getApprovalStatus() {
        return this.approvalStatus;
    }

    public void setApprovalStatus(final String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public com.mindbridgex.assessment.domain.Bloom getBloom() {
        return this.bloom;
    }

    public void setBloom(final com.mindbridgex.assessment.domain.Bloom bloom) {
        this.bloom = bloom;
    }

    public String getUnit() {
        return this.unit;
    }

    public void setUnit(final String unit) {
        this.unit = unit;
    }
}
