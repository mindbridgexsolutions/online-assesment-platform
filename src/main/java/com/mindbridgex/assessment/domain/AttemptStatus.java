package com.mindbridgex.assessment.domain;

public enum AttemptStatus {
    IN_PROGRESS("In progress"),
    SUBMITTED("Submitted - awaiting grading"),
    GRADED("Graded");

    private final String label;

    AttemptStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
