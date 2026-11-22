package com.mindbridgex.assessment.domain;

public enum SubmitReason {
    STUDENT("Submitted by student"),
    TIMEOUT("Auto-submitted: time over"),
    VIOLATION("Auto-submitted: too many focus losses"),
    TEACHER("Closed by teacher");

    private final String label;

    SubmitReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
