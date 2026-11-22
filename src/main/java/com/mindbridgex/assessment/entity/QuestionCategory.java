package com.mindbridgex.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_categories")
public class QuestionCategory extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;
    @Column(nullable = false, length = 120)
    private String name;

    public Subject getSubject() {
        return this.subject;
    }

    public String getName() {
        return this.name;
    }

    public void setSubject(final Subject subject) {
        this.subject = subject;
    }

    public void setName(final String name) {
        this.name = name;
    }
}
