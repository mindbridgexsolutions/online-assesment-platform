package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.dto.BatchForm;
import com.mindbridgex.assessment.entity.Batch;
import com.mindbridgex.assessment.entity.User;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.BatchRepository;
import com.mindbridgex.assessment.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BatchService {

    public record BatchRow(Long id, String name, String code, String description, boolean active, int students) {
    }


    /**
     * Outcome of adding students by username / roll number.
     */
    public record AddResult(int added, int alreadyMember, List<String> notFound) {
    }

    private final BatchRepository batches;
    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<BatchRow> list() {
        return batches.findAllByOrderByNameAsc().stream().map(b -> new BatchRow(b.getId(), b.getName(), b.getCode(), b.getDescription(), b.isActive(), b.getStudentIds().size())).toList();
    }

    @Transactional(readOnly = true)
    public List<Batch> activeBatches() {
        return batches.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Batch get(Long id) {
        return batches.findById(id).orElseThrow(() -> AppException.notFound("Batch"));
    }

    @Transactional(readOnly = true)
    public BatchForm toForm(Long id) {
        Batch b = get(id);
        BatchForm f = new BatchForm();
        f.setId(b.getId());
        f.setName(b.getName());
        f.setCode(b.getCode());
        f.setDescription(b.getDescription());
        f.setActive(b.isActive());
        return f;
    }

    @Transactional(readOnly = true)
    public List<User> members(Long batchId) {
        Batch b = get(batchId);
        if (b.getStudentIds().isEmpty()) {
            return List.of();
        }
        return users.findByIdsAndRole(b.getStudentIds(), RoleType.STUDENT);
    }

    @Transactional
    public Batch save(BatchForm form) {
        String code = form.getCode().trim();
        Batch b;
        if (form.getId() == null) {
            if (batches.existsByCodeIgnoreCase(code)) {
                throw AppException.conflict("Batch code \'" + code + "\' already exists.");
            }
            b = new Batch();
        } else {
            b = get(form.getId());
            if (batches.existsByCodeIgnoreCaseAndIdNot(code, b.getId())) {
                throw AppException.conflict("Batch code \'" + code + "\' already exists.");
            }
        }
        b.setName(form.getName().trim());
        b.setCode(code);
        b.setDescription(form.getDescription() == null || form.getDescription().isBlank() ? null : form.getDescription().trim());
        b.setActive(form.isActive());
        batches.save(b);
        audit.log(form.getId() == null ? "BATCH_CREATED" : "BATCH_UPDATED", "Batch", b.getId(), b.getCode());
        return b;
    }

    /**
     * Adds students identified by username, e-mail or roll number (one token per line / comma).
     */
    @Transactional
    public AddResult addStudents(Long batchId, String raw) {
        Batch b = get(batchId);
        Set<String> tokens = Arrays.stream(raw == null ? new String[0] : raw.split("[\\r\\n,;]+")).map(String::trim).filter(s -> !s.isEmpty()).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(LinkedHashSet::new));
        if (tokens.isEmpty()) {
            throw AppException.bad("Enter at least one username, e-mail or roll number.");
        }
        List<User> students = users.findActiveByRole(RoleType.STUDENT);
        Map<String, User> index = new java.util.HashMap<>();
        for (User s : students) {
            index.putIfAbsent(s.getUsername().toLowerCase(Locale.ROOT), s);
            index.putIfAbsent(s.getEmail().toLowerCase(Locale.ROOT), s);
            if (s.getRollNo() != null) {
                index.putIfAbsent(s.getRollNo().toLowerCase(Locale.ROOT), s);
            }
        }
        int added = 0;
        int already = 0;
        List<String> missing = new ArrayList<>();
        for (String t : tokens) {
            User u = index.get(t);
            if (u == null) {
                missing.add(t);
            } else if (b.getStudentIds().add(u.getId())) {
                added++;
            } else {
                already++;
            }
        }
        batches.save(b);
        audit.log("BATCH_STUDENTS_ADDED", "Batch", batchId, added + " added");
        return new AddResult(added, already, missing);
    }

    @Transactional
    public void addStudentIds(Long batchId, Set<Long> studentIds) {
        Batch b = get(batchId);
        b.getStudentIds().addAll(studentIds);
        batches.save(b);
    }

    @Transactional
    public void removeStudent(Long batchId, Long studentId) {
        Batch b = get(batchId);
        if (b.getStudentIds().remove(studentId)) {
            batches.save(b);
            audit.log("BATCH_STUDENT_REMOVED", "Batch", batchId, "student " + studentId);
        }
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        Batch b = get(id);
        b.setActive(active);
        batches.save(b);
        audit.log(active ? "BATCH_ACTIVATED" : "BATCH_DEACTIVATED", "Batch", id, b.getCode());
    }

    public BatchService(final BatchRepository batches, final UserRepository users, final AuditService audit) {
        this.batches = batches;
        this.users = users;
        this.audit = audit;
    }
}
