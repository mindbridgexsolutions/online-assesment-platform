package com.mindbridgex.assessment.dto;

/**
 * One row in the question editor: an option, an accepted answer, or a matching pair (text = left, matchText = right).
 */
public class OptionForm {
    private Long id;
    private String text;
    private String matchText;
    private boolean correct;

    private String imagePath;
    private org.springframework.web.multipart.MultipartFile image;
    private boolean removeImage;

    public Long getId() {
        return this.id;
    }

    public String getText() {
        return this.text;
    }

    public String getMatchText() {
        return this.matchText;
    }

    public boolean isCorrect() {
        return this.correct;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setText(final String text) {
        this.text = text;
    }

    public void setMatchText(final String matchText) {
        this.matchText = matchText;
    }

    public void setCorrect(final boolean correct) {
        this.correct = correct;
    }

    public OptionForm() {
    }

    public OptionForm(final Long id, final String text, final String matchText, final boolean correct) {
        this.id = id;
        this.text = text;
        this.matchText = matchText;
        this.correct = correct;
    }

    public String getImagePath() {
        return this.imagePath;
    }

    public void setImagePath(final String imagePath) {
        this.imagePath = imagePath;
    }

    public org.springframework.web.multipart.MultipartFile getImage() {
        return this.image;
    }

    public void setImage(final org.springframework.web.multipart.MultipartFile image) {
        this.image = image;
    }

    public boolean isRemoveImage() {
        return this.removeImage;
    }

    public void setRemoveImage(final boolean removeImage) {
        this.removeImage = removeImage;
    }
}
