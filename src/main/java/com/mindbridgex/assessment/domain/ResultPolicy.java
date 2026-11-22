package com.mindbridgex.assessment.domain;

public enum ResultPolicy {
    IMMEDIATE("Immediately after grading"),
    AFTER_WINDOW("After the exam window closes"),
    MANUAL("When the teacher releases results"),
    SCHEDULED("On a date and time the teacher chooses");

    private final String label;

    ResultPolicy(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
