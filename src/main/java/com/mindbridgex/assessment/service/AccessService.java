package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.entity.Subject;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.SubjectRepository;
import com.mindbridgex.assessment.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Data-level authorisation: a teacher may only touch content of subjects they are assigned to.
 */
@Service
public class AccessService {
    private final SubjectRepository subjects;

    @Transactional(readOnly = true)
    public List<Subject> teacherSubjects(AuthUser user) {
        if (user.isSuperAdmin()) {
            return subjects.findByActiveTrueOrderByNameAsc();
        }
        if (user.isIqac() && !user.isTeacher()) {
            return subjects.findByActiveTrueOrderByNameAsc();
        }
        List<Subject> mine = new java.util.ArrayList<>(subjects.findActiveByTeacher(user.getId()));
        if (user.isHod()) {
            for (Subject s : subjects.findActiveInBranchOf(user.getId())) {
                if (mine.stream().noneMatch(x -> x.getId().equals(s.getId()))) {
                    mine.add(s);
                }
            }
        }
        return mine;
    }

    @Transactional(readOnly = true)
    public Set<Long> teacherSubjectIds(AuthUser user) {
        return teacherSubjects(user).stream().map(Subject::getId).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public void requireSubject(AuthUser user, Long subjectId) {
        if (user.isSuperAdmin() && subjectId != null) {
            return;
        }
        if (subjectId != null && user.isIqac()) {
            return;
        }
        if (subjectId != null && user.isHod() && subjects.isInBranchOf(user.getId(), subjectId)) {
            return;
        }
        if (subjectId == null || !subjects.isTeacherAssigned(user.getId(), subjectId)) {
            throw AppException.forbidden("You are not assigned to this subject.");
        }
    }

    public AccessService(final SubjectRepository subjects) {
        this.subjects = subjects;
    }
}
