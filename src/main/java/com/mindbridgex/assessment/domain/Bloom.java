package com.mindbridgex.assessment.domain;

/** Bloom's taxonomy levels used to tag questions. */
public enum Bloom {
    REMEMBER("Remember"), UNDERSTAND("Understand"), APPLY("Apply"), ANALYZE("Analyze"), EVALUATE("Evaluate"), CREATE("Create");

    private final String label;

    Bloom(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Bloom parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        if (t.matches("[1-6]")) { // the paper template uses 1 = Remember ... 6 = Create
            return values()[Integer.parseInt(t) - 1];
        }
        for (Bloom b : values()) {
            if (b.name().equalsIgnoreCase(s.trim()) || b.label.equalsIgnoreCase(s.trim())) {
                return b;
            }
        }
        throw new IllegalArgumentException(s);
    }
}
