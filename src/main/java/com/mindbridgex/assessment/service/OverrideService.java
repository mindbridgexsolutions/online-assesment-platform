package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.domain.ExamStatus;
import com.mindbridgex.assessment.entity.Exam;
import com.mindbridgex.assessment.entity.Question;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.ExamRepository;
import com.mindbridgex.assessment.repository.QuestionRepository;
import com.mindbridgex.assessment.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Super Administrator overrides of the normal workflow. Every call needs a written reason and is audit-logged. */
@Service
public class OverrideService {
    private final ExamRepository exams;
    private final QuestionRepository questions;
    private final AuditService audit;
    private final AccessService access;

    public OverrideService(ExamRepository exams, QuestionRepository questions, AuditService audit, AccessService access) {
        this.access = access;
        this.exams = exams;
        this.questions = questions;
        this.audit = audit;
    }

    static void requireSuper(AuthUser u) {
        if (u == null || !u.isSuperAdmin()) {
            throw AppException.forbidden("Only a Super Administrator can do this.");
        }
    }

    static String reason(String r) {
        String t = r == null ? "" : r.trim();
        if (t.length() < 5) {
            throw AppException.bad("Please write a reason (at least 5 characters). It is saved in the audit log.");
        }
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    @Transactional
    public void examApproval(AuthUser user, Long examId, boolean approve, String reason) {
        requireSuper(user);
        String why = approve && (reason == null || reason.isBlank()) ? "Approved" : reason(reason);
        Exam e = exams.findById(examId).orElseThrow(() -> AppException.notFound("Exam"));
        e.setApprovalStatus(approve ? "APPROVED" : "REJECTED");
        if (!approve && e.getStatus() == ExamStatus.PUBLISHED) {
            e.setStatus(ExamStatus.DRAFT);
        }
        exams.save(e);
        audit.log(approve ? "OVERRIDE_EXAM_APPROVE" : "OVERRIDE_EXAM_REJECT", "Exam", examId, e.getTitle() + " - " + why);
    }

    @Transactional
    public void questionApproval(AuthUser user, Long questionId, boolean approve, String reason) {
        requireSuper(user);
        String why = approve && (reason == null || reason.isBlank()) ? "Approved" : reason(reason);
        Question q = questions.findById(questionId).orElseThrow(() -> AppException.notFound("Question"));
        q.setApprovalStatus(approve ? "APPROVED" : "REJECTED");
        questions.save(q);
        audit.log(approve ? "OVERRIDE_QUESTION_APPROVE" : "OVERRIDE_QUESTION_REJECT", "Question", questionId, why);
    }

    /** Head of Department (for subjects of their branch) or Super Admin approves or rejects an exam. */
    @Transactional
    public void hodDecision(AuthUser user, Long examId, boolean approve, String reason) {
        Exam e = exams.findById(examId).orElseThrow(() -> AppException.notFound("Exam"));
        if (!user.isSuperAdmin()) {
            if (!user.isHod()) {
                throw AppException.forbidden("Only a Head of Department can do this.");
            }
            access.requireSubject(user, e.getSubject().getId());
        }
        String why = approve && (reason == null || reason.isBlank()) ? "Approved" : reason(reason);
        String old = e.getApprovalStatus();
        e.setApprovalStatus(approve ? "APPROVED" : "REJECTED");
        exams.save(e);
        audit.log(approve ? "EXAM_APPROVED" : "EXAM_REJECTED", "Exam", examId, e.getTitle() + ": " + old + " -> " + e.getApprovalStatus() + " - " + why);
    }

    /** Super Admin releases results early, or moves the release date (the hold can be longer or shorter). */
    @Transactional
    public void setResultRelease(AuthUser user, Long examId, java.time.Instant at, String reason) {
        requireSuper(user);
        String why = reason(reason);
        if (at == null) {
            throw AppException.bad("Choose the date and time.");
        }
        Exam e = exams.findById(examId).orElseThrow(() -> AppException.notFound("Exam"));
        String old = e.getResultPolicy() + (e.getResultsPublishAt() == null ? "" : " at " + e.getResultsPublishAt());
        e.setResultPolicy(com.mindbridgex.assessment.domain.ResultPolicy.SCHEDULED);
        e.setResultsPublishAt(at);
        exams.save(e);
        audit.log("OVERRIDE_RESULT_RELEASE", "Exam", examId, old + " -> SCHEDULED at " + at + " - " + why);
    }
}
