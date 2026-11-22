package com.mindbridgex.assessment.dto;

import com.mindbridgex.assessment.domain.Difficulty;
import com.mindbridgex.assessment.domain.QuestionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class QuestionForm {
    private Long id;
    @NotNull(message = "Select a subject")
    private Long subjectId;
    private Long categoryId;
    @NotNull(message = "Select a question type")
    private QuestionType type = QuestionType.MCQ_SINGLE;
    @NotNull(message = "Select a difficulty")
    private Difficulty difficulty = Difficulty.MEDIUM;
    @Size(max = 5000, message = "Question text is too long (max 5000 characters)")
    private String text;
    @Size(max = 5000, message = "Explanation is too long (max 5000 characters)")
    private String explanation;
    @NotNull(message = "Marks are required")
    @DecimalMin(value = "0.01", message = "Marks must be greater than 0")
    @DecimalMax(value = "1000", message = "Marks must be at most 1000")
    private BigDecimal defaultMarks = BigDecimal.ONE;
    private boolean partialMarking;
    private boolean caseSensitive;
    private BigDecimal numericAnswer;
    @DecimalMin(value = "0", message = "Tolerance cannot be negative")
    private BigDecimal numericTolerance = BigDecimal.ZERO;
    /**
     * Comma separated.
     */
    @Size(max = 400, message = "Too many tags")
    private String tags;
    /**
     * TRUE_FALSE only.
     */
    private Boolean tfAnswer;
    private List<OptionForm> options = new ArrayList<>();
    private String existingImage;
    private boolean removeImage;
    private MultipartFile image;

    private Long coId;
    private boolean allowDuplicate;
    private String source;

    private com.mindbridgex.assessment.domain.Bloom bloom;
    private String unit;

    public Long getId() {
        return this.id;
    }

    public Long getSubjectId() {
        return this.subjectId;
    }

    public Long getCategoryId() {
        return this.categoryId;
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

    /**
     * Comma separated.
     */
    public String getTags() {
        return this.tags;
    }

    /**
     * TRUE_FALSE only.
     */
    public Boolean getTfAnswer() {
        return this.tfAnswer;
    }

    public List<OptionForm> getOptions() {
        return this.options;
    }

    public String getExistingImage() {
        return this.existingImage;
    }

    public boolean isRemoveImage() {
        return this.removeImage;
    }

    public MultipartFile getImage() {
        return this.image;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setSubjectId(final Long subjectId) {
        this.subjectId = subjectId;
    }

    public void setCategoryId(final Long categoryId) {
        this.categoryId = categoryId;
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

    /**
     * Comma separated.
     */
    public void setTags(final String tags) {
        this.tags = tags;
    }

    /**
     * TRUE_FALSE only.
     */
    public void setTfAnswer(final Boolean tfAnswer) {
        this.tfAnswer = tfAnswer;
    }

    public void setOptions(final List<OptionForm> options) {
        this.options = options;
    }

    public void setExistingImage(final String existingImage) {
        this.existingImage = existingImage;
    }

    public void setRemoveImage(final boolean removeImage) {
        this.removeImage = removeImage;
    }

    public void setImage(final MultipartFile image) {
        this.image = image;
    }

    public Long getCoId() {
        return this.coId;
    }

    public void setCoId(final Long coId) {
        this.coId = coId;
    }

    public boolean isAllowDuplicate() {
        return this.allowDuplicate;
    }

    public void setAllowDuplicate(final boolean allowDuplicate) {
        this.allowDuplicate = allowDuplicate;
    }

    public String getSource() {
        return this.source;
    }

    public void setSource(final String source) {
        this.source = source;
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
