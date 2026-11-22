package com.mindbridgex.assessment.domain;

public enum RoleType {
    SUPER_ADMIN("Super Administrator"),
    ADMIN("Administrator"),
    HOD("Head of Department"),
    IQAC("IQAC / NBA Coordinator"),
    TEACHER("Teacher"),
    STUDENT("Student");

    private final String label;

    RoleType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getAuthority() {
        return "ROLE_" + name();
    }
}
