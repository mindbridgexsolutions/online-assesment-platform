package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.entity.QuestionCategory;
import com.mindbridgex.assessment.entity.Subject;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.QuestionCategoryRepository;
import com.mindbridgex.assessment.repository.QuestionRepository;
import com.mindbridgex.assessment.repository.SubjectRepository;
import com.mindbridgex.assessment.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CategoryService {

    public record CategoryRow(Long id, String name, Long subjectId, String subjectName, long questionCount) {
    }

    private final QuestionCategoryRepository categories;
    private final QuestionRepository questions;
    private final SubjectRepository subjects;
    private final AccessService access;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<CategoryRow> list(AuthUser user) {
        var ids = access.teacherSubjectIds(user);
        if (ids.isEmpty()) {
            return List.of();
        }
        return categories.findBySubjectIdInOrderBySubjectNameAscNameAsc(ids).stream().map(c -> new CategoryRow(c.getId(), c.getName(), c.getSubject().getId(), c.getSubject().getName(), questions.countActiveInCategory(c.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<QuestionCategory> forSubject(AuthUser user, Long subjectId) {
        access.requireSubject(user, subjectId);
        return categories.findBySubjectIdOrderByNameAsc(subjectId);
    }

    @Transactional
    public void create(AuthUser user, Long subjectId, String name) {
        access.requireSubject(user, subjectId);
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 120) {
            throw AppException.bad("Category name is required (max 120 characters).");
        }
        if (categories.existsBySubjectIdAndNameIgnoreCase(subjectId, n)) {
            throw AppException.conflict("That category already exists for this subject.");
        }
        Subject s = subjects.findById(subjectId).orElseThrow(() -> AppException.notFound("Subject"));
        QuestionCategory c = new QuestionCategory();
        c.setSubject(s);
        c.setName(n);
        categories.save(c);
        audit.log("CATEGORY_CREATE", "QuestionCategory", c.getId(), n);
    }

    @Transactional
    public void rename(AuthUser user, Long id, String name) {
        QuestionCategory c = categories.findById(id).orElseThrow(() -> AppException.notFound("Category"));
        access.requireSubject(user, c.getSubject().getId());
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 120) {
            throw AppException.bad("Category name is required (max 120 characters).");
        }
        if (!n.equalsIgnoreCase(c.getName()) && categories.existsBySubjectIdAndNameIgnoreCase(c.getSubject().getId(), n)) {
            throw AppException.conflict("That category already exists for this subject.");
        }
        c.setName(n);
        audit.log("CATEGORY_RENAME", "QuestionCategory", id, n);
    }

    @Transactional
    public void delete(AuthUser user, Long id) {
        QuestionCategory c = categories.findById(id).orElseThrow(() -> AppException.notFound("Category"));
        access.requireSubject(user, c.getSubject().getId());
        if (questions.countActiveInCategory(id) > 0) {
            throw AppException.conflict("The category still has questions. Move or archive them first.");
        }
        try {
            categories.delete(c);
            categories.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw AppException.conflict("The category is used by an exam or archived questions and cannot be deleted.");
        }
        audit.log("CATEGORY_DELETE", "QuestionCategory", id, c.getName());
    }

    public CategoryService(final QuestionCategoryRepository categories, final QuestionRepository questions, final SubjectRepository subjects, final AccessService access, final AuditService audit) {
        this.categories = categories;
        this.questions = questions;
        this.subjects = subjects;
        this.access = access;
        this.audit = audit;
    }
}
