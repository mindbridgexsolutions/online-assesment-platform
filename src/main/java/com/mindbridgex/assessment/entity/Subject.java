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
@Table(name = "subjects")
public class Subject extends BaseEntity {
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 40, unique = true)
    private String code;
    @Column(nullable = false)
    private boolean active = true;
    @ElementCollection
    @CollectionTable(name = "teacher_subjects", joinColumns = @JoinColumn(name = "subject_id"))
    @Column(name = "teacher_id")
    @BatchSize(size = 100)
    private Set<Long> teacherIds = new HashSet<>();

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
