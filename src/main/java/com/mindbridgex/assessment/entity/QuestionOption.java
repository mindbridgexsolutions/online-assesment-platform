package com.mindbridgex.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_options")
public class QuestionOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false, columnDefinition = "text")
    private String text;
    @Column(name = "match_text", columnDefinition = "text")
    private String matchText;
    @Column(nullable = false)
    private boolean correct;

    @Column(name = "image_path", length = 300)
    private String imagePath;

    public Long getId() {
        return this.id;
    }

    public Question getQuestion() {
        return this.question;
    }

    public int getPosition() {
        return this.position;
    }

    public String getText() {
        return this.text;
    }

    public String getMatchText() {
        return this.matchText;
    }

    public boolean isCorrect() {
        return this.correct;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setQuestion(final Question question) {
        this.question = question;
    }

    public void setPosition(final int position) {
        this.position = position;
    }

    public void setText(final String text) {
        this.text = text;
    }

    public void setMatchText(final String matchText) {
        this.matchText = matchText;
    }

    public void setCorrect(final boolean correct) {
        this.correct = correct;
    }

    public String getImagePath() {
        return this.imagePath;
    }

    public void setImagePath(final String imagePath) {
        this.imagePath = imagePath;
    }
}
