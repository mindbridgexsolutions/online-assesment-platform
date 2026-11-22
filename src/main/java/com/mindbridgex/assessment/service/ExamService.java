package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.domain.AttemptStatus;
import com.mindbridgex.assessment.domain.Difficulty;
import com.mindbridgex.assessment.domain.ExamStatus;
import com.mindbridgex.assessment.dto.ExamForm;
import com.mindbridgex.assessment.entity.*;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.*;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.util.IpMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExamService {
    @org.springframework.beans.factory.annotation.Value("${app.approval-required:false}")
    private boolean approvalRequired;


    public record PickedQuestion(Long id, Long questionId, String text, String typeLabel, BigDecimal marks, int position) {
    }


    public record RuleRow(Long id, String categoryName, Difficulty difficulty, int count, BigDecimal marksEach, long available) {
    }


    public record AssignmentRow(Long id, String label, String kind, int studentCount) {
    }


    public record ExamDetail(Exam exam, List<PickedQuestion> picked, List<RuleRow> rules, List<AssignmentRow> assignments, BigDecimal totalMarks, int totalQuestions, long attemptCount, List<String> problems) {
        public boolean isStructureLocked() {
            return attemptCount > 0;
        }
    }

    private final ExamRepository exams;
    private final ExamQuestionRepository examQuestions;
    private final ExamQuestionRuleRepository rules;
    private final ExamAssignmentRepository assignments;
    private final QuestionRepository questions;
    private final QuestionCategoryRepository categories;
    private final SubjectRepository subjects;
    private final BatchRepository batches;
    private final UserRepository users;
    private final AttemptRepository attempts;
    private final AccessService access;
    private final AuditService audit;
    private final AppProperties props;

    // ---------------------------------------------------------------- queries
    @Transactional(readOnly = true)
    public Page<Exam> list(AuthUser user, int page, int size) {
        Set<Long> ids = access.teacherSubjectIds(user);
        if (ids.isEmpty()) {
            return Page.empty();
        }
        return exams.findBySubjectIdInOrderByStartAtDesc(ids, PageRequest.of(Math.max(page, 0), size));
    }

    @Transactional(readOnly = true)
    public Exam get(AuthUser user, Long id) {
        Exam e = exams.findById(id).orElseThrow(() -> AppException.notFound("Exam"));
        access.requireSubject(user, e.getSubject().getId());
        return e;
    }

    @Transactional(readOnly = true)
    public ExamDetail detail(AuthUser user, Long id) {
        Exam e = get(user, id);
        List<ExamQuestion> eqs = examQuestions.findByExamIdOrderByPositionAscIdAsc(id);
        Map<Long, Question> byId = eqs.isEmpty() ? Map.of() : questions.findByIdIn(eqs.stream().map(ExamQuestion::getQuestionId).toList()).stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        List<PickedQuestion> picked = new ArrayList<>();
        for (ExamQuestion eq : eqs) {
            Question q = byId.get(eq.getQuestionId());
            picked.add(new PickedQuestion(eq.getId(), eq.getQuestionId(), q == null ? "(deleted)" : q.getText(), q == null ? "" : q.getType().getLabel(), eq.getMarks(), eq.getPosition()));
        }
        Set<Long> manualIds = eqs.stream().map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
        List<RuleRow> ruleRows = new ArrayList<>();
        for (ExamQuestionRule r : rules.findByExamIdOrderByIdAsc(id)) {
            ruleRows.add(new RuleRow(r.getId(), r.getCategory() == null ? "Any category" : r.getCategory().getName(), r.getDifficulty(), r.getQuestionCount(), r.getMarksPerQuestion(), availableForRule(e.getSubject().getId(), r.getCategory() == null ? null : r.getCategory().getId(), r.getDifficulty(), manualIds)));
        }
        List<AssignmentRow> asg = new ArrayList<>();
        List<ExamAssignment> all = assignments.findByExamIdOrderByIdAsc(id);
        Map<Long, Batch> bMap = batches.findAllById(all.stream().map(ExamAssignment::getBatchId).filter(Objects::nonNull).toList()).stream().collect(Collectors.toMap(Batch::getId, Function.identity()));
        Map<Long, User> uMap = users.findByIdIn(all.stream().map(ExamAssignment::getStudentId).filter(Objects::nonNull).toList()).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        for (ExamAssignment a : all) {
            if (a.getBatchId() != null) {
                Batch b = bMap.get(a.getBatchId());
                asg.add(new AssignmentRow(a.getId(), b == null ? "?" : b.getName() + " (" + b.getCode() + ")", "Batch", b == null ? 0 : b.getStudentIds().size()));
            } else {
                User u = uMap.get(a.getStudentId());
                asg.add(new AssignmentRow(a.getId(), u == null ? "?" : u.getFullName() + " (" + u.getUsername() + ")", "Student", 1));
            }
        }
        BigDecimal total = totalMarks(id);
        int totalQ = (int) eqs.size() + rules.findByExamIdOrderByIdAsc(id).stream().mapToInt(ExamQuestionRule::getQuestionCount).sum();
        long attemptCount = attempts.countByExamId(id);
        List<String> problems = e.getStatus() == ExamStatus.DRAFT ? publishProblems(e, total, totalQ, ruleRows, asg.size()) : List.of();
        return new ExamDetail(e, picked, ruleRows, asg, total, totalQ, attemptCount, problems);
    }

    private long availableForRule(Long subjectId, Long categoryId, Difficulty difficulty, Set<Long> excluded) {
        return questions.findAll(QuestionSpecs.search(null, subjectId, categoryId, null, difficulty, null, null)).stream().filter(q -> !excluded.contains(q.getId())).count();
    }

    @Transactional(readOnly = true)
    public BigDecimal totalMarks(Long examId) {
        BigDecimal manual = examQuestions.sumMarks(examId);
        BigDecimal fromRules = rules.findByExamIdOrderByIdAsc(examId).stream().map(r -> r.getMarksPerQuestion().multiply(BigDecimal.valueOf(r.getQuestionCount()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        return manual.add(fromRules);
    }

    // ---------------------------------------------------------------- create / update
    public ExamForm toForm(AuthUser user, Long id) {
        Exam e = get(user, id);
        ExamForm f = new ExamForm();
        f.setId(e.getId());
        f.setSubjectId(e.getSubject().getId());
        f.setTitle(e.getTitle());
        f.setInstructions(e.getInstructions());
        f.setDurationMinutes(e.getDurationMinutes());
        f.setStartAt(LocalDateTime.ofInstant(e.getStartAt(), props.zone()));
        f.setEndAt(LocalDateTime.ofInstant(e.getEndAt(), props.zone()));
        f.setMaxAttempts(e.getMaxAttempts());
        f.setPassingMarks(e.getPassingMarks());
        f.setNegativeMarking(e.isNegativeMarking());
        f.setNegativeFraction(e.getNegativeFraction());
        f.setShuffleQuestions(e.isShuffleQuestions());
        f.setShuffleOptions(e.isShuffleOptions());
        f.setResultPolicy(e.getResultPolicy());
        f.setExamType(e.getExamType());
        f.setResultDelayDays(e.getResultDelayDays());
        f.setResultsPublishAt(e.getResultsPublishAt() == null ? null : LocalDateTime.ofInstant(e.getResultsPublishAt(), props.zone()));
        f.setReviewAllowed(e.isReviewAllowed());
        f.setAllowedIps(e.getAllowedIps());
        f.setMaxFocusLosses(e.getMaxFocusLosses());
        f.setBlockCopyPaste(e.isBlockCopyPaste());
        return f;
    }

    @Transactional
    public Exam save(AuthUser user, ExamForm f) {
        access.requireSubject(user, f.getSubjectId());
        Instant start = f.getStartAt().atZone(props.zone()).toInstant();
        Instant end = f.getEndAt().atZone(props.zone()).toInstant();
        if (!end.isAfter(start)) {
            throw AppException.bad("The end time must be after the start time.");
        }
        String ips = normaliseIps(f.getAllowedIps());
        boolean isNew = f.getId() == null;
        Exam e = isNew ? new Exam() : get(user, f.getId());
        boolean locked = !isNew && attempts.countByExamId(e.getId()) > 0;
        if (e.getStatus() == ExamStatus.ARCHIVED) {
            throw AppException.conflict("Archived exams cannot be edited.");
        }
        if (!locked) {
            e.setSubject(subjects.findById(f.getSubjectId()).orElseThrow(() -> AppException.notFound("Subject")));
            e.setDurationMinutes(f.getDurationMinutes());
            e.setMaxAttempts(f.getMaxAttempts());
            e.setPassingMarks(f.getPassingMarks());
            e.setNegativeMarking(f.isNegativeMarking());
            e.setNegativeFraction(f.getNegativeFraction() == null ? BigDecimal.ZERO : f.getNegativeFraction());
            e.setShuffleQuestions(f.isShuffleQuestions());
            e.setShuffleOptions(f.isShuffleOptions());
            e.setStartAt(start);
        }
        e.setTitle(f.getTitle().trim());
        e.setInstructions(f.getInstructions() == null || f.getInstructions().isBlank() ? null : f.getInstructions().trim());
        e.setEndAt(end);
        e.setExamType(f.getExamType() == null ? com.mindbridgex.assessment.domain.ExamType.INTERNAL : f.getExamType());
        e.setResultDelayDays(f.getResultDelayDays() == null ? null : Math.max(0, Math.min(365, f.getResultDelayDays())));
        if (approvalRequired && (isNew || (e.getStatus() == ExamStatus.DRAFT && "APPROVED".equals(e.getApprovalStatus())))) {
            e.setApprovalStatus("DRAFT");
        }
        e.setResultPolicy(f.getResultPolicy());
        if (f.getResultPolicy() == com.mindbridgex.assessment.domain.ResultPolicy.SCHEDULED) {
            if (f.getResultsPublishAt() == null && e.getResultDelayDays() == null) {
                throw AppException.bad("Choose how many days after the exam to hold results, or the exact date and time to publish them.");
            }
            Instant publishAt = f.getResultsPublishAt() != null ? f.getResultsPublishAt().atZone(props.zone()).toInstant()
                    : end.plus(java.time.Duration.ofDays(e.getResultDelayDays()));
            if (publishAt.isBefore(end) && !publishAt.isAfter(start)) {
                throw AppException.bad("The result publishing time must not be before the exam starts.");
            }
            e.setResultsPublishAt(publishAt);
        } else {
            e.setResultsPublishAt(null);
        }
        e.setReviewAllowed(f.isReviewAllowed());
        e.setAllowedIps(ips);
        e.setMaxFocusLosses(f.getMaxFocusLosses());
        e.setBlockCopyPaste(f.isBlockCopyPaste());
        Exam saved = exams.save(e);
        audit.log(isNew ? "EXAM_CREATE" : "EXAM_UPDATE", "Exam", saved.getId(), saved.getTitle());
        return saved;
    }

    private static String normaliseIps(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split("[,;\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!IpMatcher.isValidRule(part)) {
                throw AppException.bad("Invalid IP address or range: " + part);
            }
            out.add(part.trim());
        }
        return out.isEmpty() ? null : String.join(", ", out);
    }

    // ---------------------------------------------------------------- questions & rules
    @Transactional(readOnly = true)
    public Exam requireEditable(AuthUser user, Long examId) {
        return editable(user, examId);
    }

    private Exam editable(AuthUser user, Long examId) {
        Exam e = get(user, examId);
        if (e.getStatus() == ExamStatus.ARCHIVED) {
            throw AppException.conflict("Archived exams cannot be edited.");
        }
        if (attempts.countByExamId(examId) > 0) {
            throw AppException.conflict("Students have already started this exam, so its questions can no longer change.");
        }
        return e;
    }

    @Transactional(readOnly = true)
    public List<Question> pickable(AuthUser user, Long examId, Long categoryId, com.mindbridgex.assessment.domain.QuestionType type, Difficulty difficulty, String q) {
        Exam e = get(user, examId);
        Set<Long> taken = examQuestions.findByExamIdOrderByPositionAscIdAsc(examId).stream().map(ExamQuestion::getQuestionId).collect(Collectors.toSet());
        return questions.findAll(QuestionSpecs.search(null, e.getSubject().getId(), categoryId, type, difficulty, null, q), PageRequest.of(0, 200)).stream().filter(x -> !taken.contains(x.getId())).toList();
    }

    @Transactional
    public int addQuestions(AuthUser user, Long examId, Collection<Long> questionIds) {
        Exam e = editable(user, examId);
        int pos = examQuestions.maxPosition(examId);
        int added = 0;
        for (Question q : questions.findByIdIn(questionIds)) {
            if (!q.getSubject().getId().equals(e.getSubject().getId()) || q.isArchived() || "REJECTED".equals(q.getApprovalStatus()) || examQuestions.existsByExamIdAndQuestionId(examId, q.getId())) {
                continue;
            }
            ExamQuestion eq = new ExamQuestion();
            eq.setExamId(examId);
            eq.setQuestionId(q.getId());
            eq.setMarks(q.getDefaultMarks());
            eq.setPosition(++pos);
            examQuestions.save(eq);
            added++;
        }
        audit.log("EXAM_QUESTIONS_ADD", "Exam", examId, added + " added");
        return added;
    }

    @Transactional
    public void updateMarks(AuthUser user, Long examId, Long examQuestionId, BigDecimal marks) {
        editable(user, examId);
        if (marks == null || marks.signum() <= 0 || marks.compareTo(new BigDecimal("1000")) > 0) {
            throw AppException.bad("Marks must be between 0.01 and 1000.");
        }
        ExamQuestion eq = examQuestions.findByIdAndExamId(examQuestionId, examId).orElseThrow(() -> AppException.notFound("Question"));
        eq.setMarks(marks);
    }

    @Transactional
    public void removeQuestion(AuthUser user, Long examId, Long examQuestionId) {
        editable(user, examId);
        ExamQuestion eq = examQuestions.findByIdAndExamId(examQuestionId, examId).orElseThrow(() -> AppException.notFound("Question"));
        examQuestions.delete(eq);
        audit.log("EXAM_QUESTION_REMOVE", "Exam", examId, "question " + eq.getQuestionId());
    }

    @Transactional
    public void addRule(AuthUser user, Long examId, Long categoryId, Difficulty difficulty, int count, BigDecimal marksEach) {
        Exam e = editable(user, examId);
        if (count < 1 || count > 200) {
            throw AppException.bad("Number of questions must be between 1 and 200.");
        }
        if (marksEach == null || marksEach.signum() <= 0 || marksEach.compareTo(new BigDecimal("1000")) > 0) {
            throw AppException.bad("Marks per question must be between 0.01 and 1000.");
        }
        ExamQuestionRule r = new ExamQuestionRule();
        r.setExamId(examId);
        if (categoryId != null) {
            QuestionCategory c = categories.findById(categoryId).orElseThrow(() -> AppException.notFound("Category"));
            if (!c.getSubject().getId().equals(e.getSubject().getId())) {
                throw AppException.bad("The category belongs to a different subject.");
            }
            r.setCategory(c);
        }
        r.setDifficulty(difficulty);
        r.setQuestionCount(count);
        r.setMarksPerQuestion(marksEach);
        rules.save(r);
        audit.log("EXAM_RULE_ADD", "Exam", examId, count + " random questions");
    }

    @Transactional
    public void removeRule(AuthUser user, Long examId, Long ruleId) {
        editable(user, examId);
        rules.delete(rules.findByIdAndExamId(ruleId, examId).orElseThrow(() -> AppException.notFound("Rule")));
    }

    // ---------------------------------------------------------------- assignments
    @Transactional
    public void assignBatches(AuthUser user, Long examId, Collection<Long> batchIds) {
        Exam e = get(user, examId);
        notArchived(e);
        int n = 0;
        for (Batch b : batches.findAllById(batchIds)) {
            if (b.isActive() && !assignments.existsByExamIdAndBatchId(examId, b.getId())) {
                ExamAssignment a = new ExamAssignment();
                a.setExamId(examId);
                a.setBatchId(b.getId());
                assignments.save(a);
                n++;
            }
        }
        audit.log("EXAM_ASSIGN_BATCH", "Exam", examId, n + " batch(es)");
    }

    /**
     * Returns usernames that could not be resolved to an active student.
     */
    @Transactional
    public List<String> assignStudents(AuthUser user, Long examId, String identifiers) {
        Exam e = get(user, examId);
        notArchived(e);
        List<String> unknown = new ArrayList<>();
        int n = 0;
        for (String raw : identifiers == null ? new String[0] : identifiers.split("[,;\\n\\r]+")) {
            String id = raw.trim();
            if (id.isEmpty()) {
                continue;
            }
            Optional<User> u = users.findByUsernameIgnoreCase(id);
            if (u.isEmpty() || !u.get().isActive() || !u.get().getRoles().contains(com.mindbridgex.assessment.domain.RoleType.STUDENT)) {
                unknown.add(id);
                continue;
            }
            if (!assignments.existsByExamIdAndStudentId(examId, u.get().getId())) {
                ExamAssignment a = new ExamAssignment();
                a.setExamId(examId);
                a.setStudentId(u.get().getId());
                assignments.save(a);
                n++;
            }
        }
        audit.log("EXAM_ASSIGN_STUDENT", "Exam", examId, n + " student(s)");
        return unknown;
    }

    @Transactional
    public void removeAssignment(AuthUser user, Long examId, Long assignmentId) {
        Exam e = get(user, examId);
        notArchived(e);
        assignments.delete(assignments.findByIdAndExamId(assignmentId, examId).orElseThrow(() -> AppException.notFound("Assignment")));
    }

    private void notArchived(Exam e) {
        if (e.getStatus() == ExamStatus.ARCHIVED) {
            throw AppException.conflict("Archived exams cannot be edited.");
        }
    }

    // ---------------------------------------------------------------- lifecycle
    private List<String> publishProblems(Exam e, BigDecimal total, int totalQ, List<RuleRow> ruleRows, int assignmentCount) {
        List<String> p = new ArrayList<>();
        if (totalQ == 0) {
            p.add("Add at least one question or random-selection rule.");
        }
        if (total.signum() <= 0 && totalQ > 0) {
            p.add("Total marks must be greater than zero.");
        }
        if (e.getPassingMarks().compareTo(total) > 0) {
            p.add("Passing marks (" + e.getPassingMarks().stripTrailingZeros().toPlainString() + ") exceed total marks (" + total.stripTrailingZeros().toPlainString() + ").");
        }
        for (RuleRow r : ruleRows) {
            if (r.available() < r.count()) {
                p.add("Rule \'" + r.categoryName() + (r.difficulty() == null ? "" : " / " + r.difficulty().getLabel()) + "\' needs " + r.count() + " questions but only " + r.available() + " are available.");
            }
        }
        if (approvalRequired && ("DRAFT".equals(e.getApprovalStatus()) || "PENDING".equals(e.getApprovalStatus()))) {
            p.add("The Head of Department must approve this exam first (use \"Send for approval\"). Current status: " + e.getApprovalStatus().toLowerCase() + ".");
        }
        if ("REJECTED".equals(e.getApprovalStatus())) {
            p.add("A Super Administrator has rejected this exam. Ask them to approve it again.");
        }
        if (assignmentCount == 0) {
            p.add("Assign the exam to at least one batch or student.");
        }
        if (!e.getEndAt().isAfter(Instant.now())) {
            p.add("The exam window has already ended. Edit the end time.");
        }
        return p;
    }

    @Transactional
    public void publish(AuthUser user, Long examId) {
        ExamDetail d = detail(user, examId);
        if (d.exam().getStatus() != ExamStatus.DRAFT) {
            throw AppException.conflict("Only draft exams can be published.");
        }
        if (!d.problems().isEmpty()) {
            throw AppException.bad("Cannot publish yet: " + String.join(" ", d.problems()));
        }
        Exam e = exams.findById(examId).orElseThrow();
        e.setStatus(ExamStatus.PUBLISHED);
        audit.log("EXAM_PUBLISH", "Exam", examId, e.getTitle());
    }

    @Transactional
    public void requestApproval(AuthUser user, Long examId) {
        Exam e = get(user, examId);
        if (e.getStatus() != ExamStatus.DRAFT) {
            throw AppException.conflict("Only draft exams can be sent for approval.");
        }
        if (examQuestions.countByExamId(examId) + rules.findByExamIdOrderByIdAsc(examId).size() == 0) {
            throw AppException.bad("Add questions before sending the exam for approval.");
        }
        e.setApprovalStatus("PENDING");
        audit.log("EXAM_APPROVAL_REQUESTED", "Exam", examId, e.getTitle());
    }

    @Transactional
    public void unpublish(AuthUser user, Long examId) {
        Exam e = get(user, examId);
        if (e.getStatus() != ExamStatus.PUBLISHED) {
            throw AppException.conflict("The exam is not published.");
        }
        if (attempts.countByExamId(examId) > 0) {
            throw AppException.conflict("Students have already started this exam. Archive it instead.");
        }
        e.setStatus(ExamStatus.DRAFT);
        audit.log("EXAM_UNPUBLISH", "Exam", examId, e.getTitle());
    }

    @Transactional
    public void archive(AuthUser user, Long examId) {
        Exam e = get(user, examId);
        if (attempts.countByExamIdAndStatus(examId, AttemptStatus.IN_PROGRESS) > 0) {
            throw AppException.conflict("Some students are still taking this exam.");
        }
        e.setStatus(ExamStatus.ARCHIVED);
        audit.log("EXAM_ARCHIVE", "Exam", examId, e.getTitle());
    }

    @Transactional
    public void setResultsReleased(AuthUser user, Long examId, boolean released) {
        Exam e = get(user, examId);
        e.setResultsReleased(released);
        audit.log(released ? "EXAM_RESULTS_RELEASE" : "EXAM_RESULTS_HIDE", "Exam", examId, e.getTitle());
    }

    public ExamService(final ExamRepository exams, final ExamQuestionRepository examQuestions, final ExamQuestionRuleRepository rules, final ExamAssignmentRepository assignments, final QuestionRepository questions, final QuestionCategoryRepository categories, final SubjectRepository subjects, final BatchRepository batches, final UserRepository users, final AttemptRepository attempts, final AccessService access, final AuditService audit, final AppProperties props) {
        this.exams = exams;
        this.examQuestions = examQuestions;
        this.rules = rules;
        this.assignments = assignments;
        this.questions = questions;
        this.categories = categories;
        this.subjects = subjects;
        this.batches = batches;
        this.users = users;
        this.attempts = attempts;
        this.access = access;
        this.audit = audit;
        this.props = props;
    }
}
