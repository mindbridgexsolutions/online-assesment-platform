package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.domain.Difficulty;
import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.dto.OptionForm;
import com.mindbridgex.assessment.dto.QuestionForm;
import com.mindbridgex.assessment.entity.Question;
import com.mindbridgex.assessment.entity.QuestionCategory;
import com.mindbridgex.assessment.entity.QuestionOption;
import com.mindbridgex.assessment.entity.Subject;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.QuestionCategoryRepository;
import com.mindbridgex.assessment.repository.QuestionRepository;
import com.mindbridgex.assessment.repository.QuestionSpecs;
import com.mindbridgex.assessment.repository.SubjectRepository;
import com.mindbridgex.assessment.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class QuestionService {
    private final QuestionRepository questions;
    private final QuestionCategoryRepository categories;
    private final SubjectRepository subjects;
    private final AccessService access;
    private final FileStorageService storage;
    private final AuditService audit;
    private final QuestionHasher hasher;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;


    /**
     * Flat, session-independent row for the question list (the open-in-view filter is disabled).
     */
    public record QuestionRow(Long id, String text, String subjectName, String categoryName, QuestionType type, Difficulty difficulty, BigDecimal defaultMarks, List<String> tags) {
    }

    @Transactional(readOnly = true)
    public Page<QuestionRow> search(AuthUser user, Long subjectId, Long categoryId, QuestionType type, Difficulty difficulty, String tag, String q, int page, int size) {
        Set<Long> allowed = access.teacherSubjectIds(user);
        if (subjectId != null && !allowed.contains(subjectId)) {
            subjectId = null;
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        return questions.findAll(QuestionSpecs.search(allowed, subjectId, categoryId, type, difficulty, tag, q), pageable).map(x -> new QuestionRow(x.getId(), x.getText(), x.getSubject().getName(), x.getCategory() == null ? "" : x.getCategory().getName(), x.getType(), x.getDifficulty(), x.getDefaultMarks(), new ArrayList<>(x.getTags())));
    }

    @Transactional(readOnly = true)
    public Question get(AuthUser user, Long id) {
        Question q = questions.findById(id).orElseThrow(() -> AppException.notFound("Question"));
        access.requireSubject(user, q.getSubject().getId());
        return q;
    }

    @Transactional(readOnly = true)
    public QuestionForm toForm(AuthUser user, Long id) {
        Question q = get(user, id);
        QuestionForm f = new QuestionForm();
        f.setId(q.getId());
        f.setSubjectId(q.getSubject().getId());
        f.setCategoryId(q.getCategory() == null ? null : q.getCategory().getId());
        f.setType(q.getType());
        f.setDifficulty(q.getDifficulty());
        f.setText(IMAGE_ONLY_TEXT.equals(q.getText()) ? "" : q.getText());
        f.setExplanation(q.getExplanation());
        f.setDefaultMarks(q.getDefaultMarks());
        f.setPartialMarking(q.isPartialMarking());
        f.setCaseSensitive(q.isCaseSensitive());
        f.setNumericAnswer(q.getNumericAnswer());
        f.setNumericTolerance(q.getNumericTolerance());
        f.setTags(String.join(", ", q.getTags()));
        f.setExistingImage(q.getImagePath());
        f.setCoId(q.getCoId());
        f.setBloom(q.getBloom());
        f.setUnit(q.getUnit());
        List<OptionForm> opts = new ArrayList<>();
        for (QuestionOption o : q.getOptions()) {
            OptionForm of = new OptionForm(o.getId(), o.getText(), o.getMatchText(), o.isCorrect());
            of.setImagePath(o.getImagePath());
            opts.add(of);
        }
        if (q.getType() == QuestionType.TRUE_FALSE) {
            f.setTfAnswer(q.getOptions().stream().anyMatch(o -> o.isCorrect() && "True".equalsIgnoreCase(o.getText())));
            opts.clear();
        }
        f.setOptions(opts);
        return f;
    }

    /**
     * Server-side validation of the type-specific answer key. Returns problems, empty if the form is valid.
     */
    /** Stored as the text of a question that consists of a picture only (the student screen does not show it). */
    public static final String IMAGE_ONLY_TEXT = "[Image question]";

    public List<String> validate(QuestionForm f) {
        List<String> errs = new ArrayList<>();
        boolean hasText = f.getText() != null && !f.getText().isBlank();
        boolean hasPicture = (f.getImage() != null && !f.getImage().isEmpty())
                || (f.getExistingImage() != null && !f.getExistingImage().isBlank() && !f.isRemoveImage());
        if (!hasText && !hasPicture) {
            errs.add("Enter the question text or add a question picture.");
        }
        List<OptionForm> opts = cleanOptions(f);
        QuestionType t = f.getType();
        if (t == null) {
            return errs;
        }
        switch (t) {
            case MCQ_SINGLE -> {
                if (opts.size() < 2) errs.add("Add at least two options.");
                if (opts.stream().filter(OptionForm::isCorrect).count() != 1) errs.add("Mark exactly one option as correct.");
            }
            case MCQ_MULTI -> {
                if (opts.size() < 2) errs.add("Add at least two options.");
                if (opts.stream().noneMatch(OptionForm::isCorrect)) errs.add("Mark at least one option as correct.");
            }
            case TRUE_FALSE -> {
                if (f.getTfAnswer() == null) errs.add("Choose whether the statement is True or False.");
            }
            case FILL_BLANK, SHORT_ANSWER -> {
                if (opts.isEmpty()) errs.add(t == QuestionType.FILL_BLANK ? "Add at least one accepted answer." : "Add at least one expected keyword.");
            }
            case MATCHING -> {
                if (opts.size() < 2) errs.add("Add at least two pairs.");
                if (opts.stream().anyMatch(o -> o.getMatchText() == null || o.getMatchText().isBlank())) errs.add("Every pair needs both a left and a right item.");
            }
            case NUMERIC -> {
                if (f.getNumericAnswer() == null) errs.add("Enter the correct numeric answer.");
            }
            case ESSAY -> {
            }
        }
        if (opts.stream().anyMatch(o -> o.getText().length() > 1000)) {
            errs.add("Option text is too long (max 1000 characters).");
        }
        return errs;
    }

    private static List<OptionForm> cleanOptions(QuestionForm f) {
        List<OptionForm> out = new ArrayList<>();
        if (f.getType() == null || f.getOptions() == null) {
            return out;
        }
        boolean needsOptions = f.getType().isChoice() && f.getType() != QuestionType.TRUE_FALSE || f.getType().isTextKey() || f.getType() == QuestionType.MATCHING;
        if (!needsOptions) {
            return out;
        }
        for (OptionForm o : f.getOptions()) {
            boolean hasText = o != null && o.getText() != null && !o.getText().isBlank();
            boolean hasImage = o != null && f.getType().isChoice() && ((o.getImage() != null && !o.getImage().isEmpty())
                    || (o.getImagePath() != null && !o.getImagePath().isBlank() && !o.isRemoveImage()));
            if (!hasText && !hasImage) {
                continue;
            }
            OptionForm c = new OptionForm(o.getId(), hasText ? o.getText().trim() : "", o.getMatchText() == null ? null : o.getMatchText().trim(), o.isCorrect());
            c.setImage(o.getImage());
            c.setImagePath(o.getImagePath());
            c.setRemoveImage(o.isRemoveImage());
            out.add(c);
        }
        return out;
    }

    @Transactional
    public Question save(AuthUser user, QuestionForm f) {
        access.requireSubject(user, f.getSubjectId());
        List<String> errs = validate(f);
        if (!errs.isEmpty()) {
            throw AppException.bad(String.join(" ", errs));
        }
        Subject subject = subjects.findById(f.getSubjectId()).orElseThrow(() -> AppException.notFound("Subject"));
        QuestionCategory category = null;
        if (f.getCategoryId() != null) {
            category = categories.findById(f.getCategoryId()).orElseThrow(() -> AppException.notFound("Category"));
            if (!category.getSubject().getId().equals(subject.getId())) {
                throw AppException.bad("The category does not belong to the selected subject.");
            }
        }
        boolean isNew = f.getId() == null;
        Question q = isNew ? new Question() : get(user, f.getId());
        q.setSubject(subject);
        q.setCategory(category);
        q.setType(f.getType());
        q.setDifficulty(f.getDifficulty());
        q.setText(f.getText() == null || f.getText().isBlank() ? IMAGE_ONLY_TEXT : f.getText().trim());
        q.setExplanation(blankToNull(f.getExplanation()));
        q.setDefaultMarks(f.getDefaultMarks());
        q.setPartialMarking(f.getType() == QuestionType.MCQ_MULTI && f.isPartialMarking());
        q.setCaseSensitive(f.getType().isTextKey() && f.isCaseSensitive());
        if (f.getType() == QuestionType.NUMERIC) {
            q.setNumericAnswer(f.getNumericAnswer());
            q.setNumericTolerance(f.getNumericTolerance() == null ? BigDecimal.ZERO : f.getNumericTolerance().abs());
        } else {
            q.setNumericAnswer(null);
            q.setNumericTolerance(BigDecimal.ZERO);
        }
        q.replaceTags(parseTags(f.getTags()));
        applyImage(q, f);
        if (isNew && (f.getImage() == null || f.getImage().isEmpty()) && f.getExistingImage() != null && storage.sha256(f.getExistingImage()) != null) {
            q.setImagePath(f.getExistingImage());
        }
        rebuildOptions(q, f);
        q.setCoId(validCo(f.getCoId(), subject.getId()));
        q.setBloom(f.getBloom());
        q.setUnit(blankToNull(f.getUnit() == null ? null : (f.getUnit().length() > 80 ? f.getUnit().substring(0, 80) : f.getUnit())));
        if (isNew && f.getSource() != null && !f.getSource().isBlank()) {
            q.setSource(f.getSource().length() > 250 ? f.getSource().substring(0, 250) : f.getSource());
        }
        QuestionHasher.Hashes h = hasher.compute(q);
        q.setContentHash(h.content());
        q.setImageHash(h.image());
        List<Long> dups = questions.findDuplicates(subject.getId(), h.content(), h.image(), isNew ? null : q.getId());
        if (!dups.isEmpty() && !f.isAllowDuplicate()) {
            throw new DuplicateQuestionException(dups.get(0));
        }
        Question saved = questions.save(q);
        audit.log(isNew ? "QUESTION_CREATE" : "QUESTION_UPDATE", "Question", saved.getId(), saved.getType() + " in " + subject.getCode());
        return saved;
    }

    /**
     * Options keep their ids when the editor posts them back with an id, so that existing exams' answer keys
     * can be re-synced by option id. Everything else is replaced.
     */
    private void rebuildOptions(Question q, QuestionForm f) {
        List<OptionForm> wanted = new ArrayList<>();
        if (f.getType() == QuestionType.TRUE_FALSE) {
            boolean tf = Boolean.TRUE.equals(f.getTfAnswer());
            wanted.add(new OptionForm(null, "True", null, tf));
            wanted.add(new OptionForm(null, "False", null, !tf));
            // reuse ids positionally if the question already was true/false
            for (int i = 0; i < wanted.size() && i < q.getOptions().size(); i++) {
                if (q.getType() == QuestionType.TRUE_FALSE) {
                    wanted.get(i).setId(q.getOptions().get(i).getId());
                }
            }
        } else {
            wanted.addAll(cleanOptions(f));
        }
        java.util.Map<Long, QuestionOption> existing = new java.util.HashMap<>();
        for (QuestionOption o : q.getOptions()) {
            existing.put(o.getId(), o);
        }
        List<QuestionOption> result = new ArrayList<>();
        int pos = 0;
        for (OptionForm w : wanted) {
            QuestionOption o = w.getId() != null ? existing.remove(w.getId()) : null;
            if (o == null) {
                o = new QuestionOption();
                o.setQuestion(q);
            }
            o.setPosition(pos++);
            String img = o.getImagePath();
            if (img == null && o.getId() == null && w.getImagePath() != null && storage.sha256(w.getImagePath()) != null) {
                img = w.getImagePath();
            }
            if (w.isRemoveImage()) {
                img = null;
            }
            if (w.getImage() != null && !w.getImage().isEmpty()) {
                img = storage.storeImageOnly(w.getImage());
            }
            o.setImagePath(f.getType() == QuestionType.MCQ_SINGLE || f.getType() == QuestionType.MCQ_MULTI ? img : null);
            o.setText(w.getText());
            o.setMatchText(f.getType() == QuestionType.MATCHING ? w.getMatchText() : null);
            boolean correct = switch (f.getType()) {
                case MATCHING, FILL_BLANK, SHORT_ANSWER -> true;
                case MCQ_SINGLE, MCQ_MULTI, TRUE_FALSE -> w.isCorrect();
                default -> false;
            };
            o.setCorrect(correct);
            result.add(o);
        }
        result.removeIf(o -> (o.getText() == null || o.getText().isBlank()) && o.getImagePath() == null);
        q.getOptions().clear();
        q.getOptions().addAll(result);
    }

    private void applyImage(Question q, QuestionForm f) {
        if (f.getImage() != null && !f.getImage().isEmpty()) {
            q.setImagePath(storage.storeImage(f.getImage()));
        } else 
        // older snapshots keep referencing the old file, so it is never deleted while attempts may use it
        if (f.isRemoveImage()) {
            q.setImagePath(null);
        }
    }

    @Transactional
    public void archive(AuthUser user, Long id) {
        Question q = get(user, id);
        q.setArchived(true);
        audit.log("QUESTION_ARCHIVE", "Question", id, null);
    }

    /**
     * Permanently deletes a question from the bank. A question that an exam uses cannot be deleted (archive it instead: archived
     * questions stay in their exams). Pictures that nothing else uses are removed from storage afterwards.
     */
    @Transactional
    public void delete(AuthUser user, Long id) {
        Question q = get(user, id);
        long inExams = jdbc.sql("select count(distinct exam_id) from exam_questions where question_id = :q").param("q", id).query(Long.class).single();
        if (inExams > 0) {
            throw AppException.conflict("This question is used in " + inExams + " exam(s), so it cannot be deleted. Archive it instead: archived questions stay in those exams and disappear from new ones.");
        }
        Set<String> pictures = new LinkedHashSet<>();
        if (q.getImagePath() != null) {
            pictures.add(q.getImagePath());
        }
        q.getOptions().forEach(o -> {
            if (o.getImagePath() != null) {
                pictures.add(o.getImagePath());
            }
        });
        String text = q.getText();
        questions.delete(q);
        questions.flush();
        audit.log("QUESTION_DELETE", "Question", id, text.length() > 80 ? text.substring(0, 80) : text);
        if (!pictures.isEmpty()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String n : pictures) {
                        String like = "%" + n + "%";
                        boolean used = Boolean.TRUE.equals(jdbc.sql("select exists (select 1 from questions where image_path = :n) or exists (select 1 from question_options where image_path = :n) "
                                + "or exists (select 1 from upload_items where payload like :l) or exists (select 1 from attempt_questions where image_path = :n or options::text like :l)")
                                .param("n", n).param("l", like).query(Boolean.class).single());
                        if (!used) {
                            storage.deleteQuietly(n);
                        }
                    }
                }
            });
        }
    }

    /** One question as a list row (used to show "the question you were looking for"). */
    @Transactional(readOnly = true)
    public QuestionRow row(AuthUser user, Long id) {
        Question q = get(user, id);
        return new QuestionRow(q.getId(), q.getText(), q.getSubject().getName(), q.getCategory() == null ? null : q.getCategory().getName(), q.getType(), q.getDifficulty(), q.getDefaultMarks(),
                new ArrayList<>(q.getTags()));
    }

    static Set<String> parseTags(String raw) {
        Set<String> tags = new LinkedHashSet<>();
        if (raw != null) {
            for (String t : raw.split(",")) {
                String s = t.trim().toLowerCase(Locale.ROOT);
                if (!s.isEmpty()) {
                    tags.add(s.length() > 40 ? s.substring(0, 40) : s);
                }
                if (tags.size() >= 10) {
                    break;
                }
            }
        }
        return tags;
    }

    /** A CO may only be attached to a question of its own subject. */
    private Long validCo(Long coId, Long subjectId) {
        if (coId == null) {
            long defined = jdbc.sql("select count(*) from course_outcomes where subject_id = :s").param("s", subjectId).query(Long.class).single();
            if (defined > 0) {
                throw AppException.bad("Choose the Course Outcome (CO) this question tests. It is needed for CO-PO attainment.");
            }
            return null;
        }
        long n = jdbc.sql("select count(*) from course_outcomes where id = :c and subject_id = :s").param("c", coId).param("s", subjectId).query(Long.class).single();
        if (n == 0) {
            throw AppException.bad("The selected Course Outcome does not belong to this subject.");
        }
        return coId;
    }

    /** Thrown when an identical question already exists in the bank. */
    public static class DuplicateQuestionException extends AppException {
        private final Long existingId;

        public DuplicateQuestionException(Long existingId) {
            super(org.springframework.http.HttpStatus.CONFLICT, "This looks like a duplicate of question #" + existingId
                    + " (same text and options). Tick \"Save anyway\" if you really want both.");
            this.existingId = existingId;
        }

        public Long getExistingId() {
            return existingId;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public QuestionService(final QuestionRepository questions, final QuestionCategoryRepository categories, final SubjectRepository subjects, final AccessService access, final FileStorageService storage, final AuditService audit, final QuestionHasher hasher, final org.springframework.jdbc.core.simple.JdbcClient jdbc) {
        this.questions = questions;
        this.categories = categories;
        this.subjects = subjects;
        this.access = access;
        this.storage = storage;
        this.audit = audit;
        this.hasher = hasher;
        this.jdbc = jdbc;
    }
}
