package com.mindbridgex.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class BatchForm {
    private Long id;
    @NotBlank(message = "Batch name is required")
    @Size(max = 120)
    private String name;
    @NotBlank(message = "Batch code is required")
    @Pattern(regexp = "^[A-Za-z0-9._-]{2,40}$", message = "Code: 2-40 letters, digits, dot, underscore or dash")
    private String code;
    @Size(max = 500)
    private String description;
    private boolean active = true;

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getCode() {
        return this.code;
    }

    public String getDescription() {
        return this.description;
    }

    public boolean isActive() {
        return this.active;
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

    public void setDescription(final String description) {
        this.description = description;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }
}
