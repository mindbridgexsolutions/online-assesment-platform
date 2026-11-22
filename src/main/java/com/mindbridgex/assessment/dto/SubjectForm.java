package com.mindbridgex.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;

public class SubjectForm {
    private Long id;
    @NotBlank(message = "Subject name is required")
    @Size(max = 120)
    private String name;
    @NotBlank(message = "Subject code is required")
    @Size(max = 40, message = "Subject code can be at most 40 characters")
    private String code;
    private boolean active = true;
    private Set<Long> teacherIds = new HashSet<>();

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getCode() {
        return this.code;
    }

    public boolean isActive() {
        return this.active;
    }

    public Set<Long> getTeacherIds() {
        return this.teacherIds;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setCode(final String code) {
        this.code = code;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    public void setTeacherIds(final Set<Long> teacherIds) {
        this.teacherIds = teacherIds;
    }
}
