package com.mindbridgex.assessment.domain;

public enum QuestionType {
    MCQ_SINGLE("Multiple choice (single answer)", true),
    MCQ_MULTI("Multiple choice (multiple answers)", true),
    TRUE_FALSE("True / False", true),
    FILL_BLANK("Fill in the blank", true),
    SHORT_ANSWER("Short answer (keywords)", true),
    ESSAY("Long answer / essay (manual grading)", false),
    MATCHING("Matching", true),
    NUMERIC("Numeric", true);

    private final String label;
    private final boolean autoGraded;

    QuestionType(String label, boolean autoGraded) {
        this.label = label;
        this.autoGraded = autoGraded;
    }

    public String getLabel() {
        return label;
    }

    public boolean isAutoGraded() {
        return autoGraded;
    }

    /** Types whose answer key is a set of options with a "correct" flag. */
    public boolean isChoice() {
        return this == MCQ_SINGLE || this == MCQ_MULTI || this == TRUE_FALSE;
    }

    /** Types whose answer key is a list of accepted texts / keywords. */
    public boolean isTextKey() {
        return this == FILL_BLANK || this == SHORT_ANSWER;
    }
}
