package com.mindbridgex.assessment.domain;

public enum ExamType {
    INTERNAL("Internal / CIE"), MID_SEM("Mid-semester"), UNIT_TEST("Unit test"), MODEL("Model exam"), LAB_VIVA("Lab / practical viva"), PLACEMENT("Placement / aptitude"), GATE("GATE-style practice");

    private final String label;

    ExamType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
