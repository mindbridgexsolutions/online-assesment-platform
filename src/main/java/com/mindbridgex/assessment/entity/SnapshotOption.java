package com.mindbridgex.assessment.entity;

/**
 * One option / accepted answer / matching pair, frozen inside an {@link AttemptQuestion}.
 * {@code key} is the id of the bank option so answers survive an answer-key re-sync.
 */
public class SnapshotOption {
    private String key;
    private String text;
    private String matchText;
    private boolean correct;

    private String imagePath;

    public String getKey() {
        return this.key;
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

    public void setKey(final String key) {
        this.key = key;
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

    public SnapshotOption() {
    }

    public SnapshotOption(final String key, final String text, final String matchText, final boolean correct) {
        this.key = key;
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
}
