package com.mindbridgex.assessment.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "batches")
public class Batch extends BaseEntity {
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 40, unique = true)
    private String code;
    @Column(length = 500)
    private String description;
    @Column(nullable = false)
    private boolean active = true;
    @ElementCollection
    @CollectionTable(name = "batch_students", joinColumns = @JoinColumn(name = "batch_id"))
    @Column(name = "student_id")
    @BatchSize(size = 100)
    private Set<Long> studentIds = new HashSet<>();

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

    public Set<Long> getStudentIds() {
        return this.studentIds;
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

    public void setStudentIds(final Set<Long> studentIds) {
        this.studentIds = studentIds;
    }
}
