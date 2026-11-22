package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.dto.SubjectForm;
import com.mindbridgex.assessment.entity.Subject;
import com.mindbridgex.assessment.entity.User;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.SubjectRepository;
import com.mindbridgex.assessment.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SubjectService {

    public record SubjectRow(Long id, String name, String code, boolean active, List<String> teachers) {
    }

    private final SubjectRepository subjects;
    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<SubjectRow> list() {
        List<Subject> all = subjects.findAllByOrderByNameAsc();
        Set<Long> teacherIds = all.stream().flatMap(s -> s.getTeacherIds().stream()).collect(Collectors.toSet());
        Map<Long, User> byId = teacherIds.isEmpty() ? Map.of() : users.findByIdIn(teacherIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return all.stream().map(s -> new SubjectRow(s.getId(), s.getName(), s.getCode(), s.isActive(), s.getTeacherIds().stream().map(byId::get).filter(u -> u != null).map(User::getFullName).sorted().toList())).toList();
    }

    @Transactional(readOnly = true)
    public Subject get(Long id) {
        return subjects.findById(id).orElseThrow(() -> AppException.notFound("Subject"));
    }

    @Transactional(readOnly = true)
    public SubjectForm toForm(Long id) {
        Subject s = get(id);
        SubjectForm f = new SubjectForm();
        f.setId(s.getId());
        f.setName(s.getName());
        f.setCode(s.getCode());
        f.setActive(s.isActive());
        f.setTeacherIds(new HashSet<>(s.getTeacherIds()));
        return f;
    }

    @Transactional(readOnly = true)
    public List<Subject> all() {
        return subjects.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Subject> forTeacher(Long teacherId) {
        return subjects.findActiveByTeacher(teacherId);
    }

    @Transactional
    public Subject save(SubjectForm form) {
        String code = form.getCode().trim();
        Subject s;
        if (form.getId() == null) {
            if (subjects.existsByCodeIgnoreCase(code)) {
                throw AppException.conflict("Subject code \'" + code + "\' already exists.");
            }
            s = new Subject();
        } else {
            s = get(form.getId());
            if (subjects.existsByCodeIgnoreCaseAndIdNot(code, s.getId())) {
                throw AppException.conflict("Subject code \'" + code + "\' already exists.");
            }
        }
        s.setName(form.getName().trim());
        s.setCode(code);
        s.setActive(form.isActive());
        Set<Long> requested = form.getTeacherIds() == null ? Set.of() : form.getTeacherIds();
        Set<Long> valid = requested.isEmpty() ? Set.of() : users.findByIdsAndRole(requested, RoleType.TEACHER).stream().filter(User::isActive).map(User::getId).collect(Collectors.toSet());
        boolean changed = !valid.equals(s.getTeacherIds());
        s.getTeacherIds().clear();
        s.getTeacherIds().addAll(valid);
        subjects.save(s);
        audit.log(form.getId() == null ? "SUBJECT_CREATED" : "SUBJECT_UPDATED", "Subject", s.getId(), s.getCode() + (changed ? " teachers=" + valid : ""));
        return s;
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        Subject s = get(id);
        s.setActive(active);
        subjects.save(s);
        audit.log(active ? "SUBJECT_ACTIVATED" : "SUBJECT_DEACTIVATED", "Subject", id, s.getCode());
    }

    public SubjectService(final SubjectRepository subjects, final UserRepository users, final AuditService audit) {
        this.subjects = subjects;
        this.users = users;
        this.audit = audit;
    }
}
