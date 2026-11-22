package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.entity.Batch;
import com.mindbridgex.assessment.entity.User;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.BatchRepository;
import com.mindbridgex.assessment.repository.UserRepository;
import com.mindbridgex.assessment.security.UserLookupService;
import com.mindbridgex.assessment.util.PasswordPolicy;
import com.mindbridgex.assessment.util.TabularReader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Bulk upload of students from CSV / XLSX. Valid rows are created, invalid rows are reported with their row number
 * (partial success by design: one bad row never blocks the other 499).
 */
@Service
public class UserImportService {
    public static final int MAX_ROWS = 1000;
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._@-]{3,60}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");


    public record Credential(String username, String fullName, String password, boolean generated) {
    }


    public record RowError(int row, String message) {
    }


    public record ImportResult(List<Credential> created, List<RowError> errors, int totalRows) {
    }


    private record Candidate(int row, String username, String fullName, String email, String rollNo, String phone, String password, boolean generated, String batchCode) {
    }

    private final UserRepository users;
    private final BatchRepository batches;
    private final PasswordEncoder encoder;
    private final UserLookupService lookup;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public ImportResult importStudents(MultipartFile file, Long defaultBatchId) {
        return importUsers(file, defaultBatchId, RoleType.STUDENT);
    }

    /** Bulk upload of students (batch columns apply) or teachers (same file format; roll_no and batch are ignored). */
    public ImportResult importUsers(MultipartFile file, Long defaultBatchId, RoleType role) {
        if (role != RoleType.STUDENT && role != RoleType.TEACHER) {
            throw AppException.bad("Bulk upload is available for students and teachers only.");
        }
        final boolean student = role == RoleType.STUDENT;
        if (!student) {
            defaultBatchId = null;
        }
        if (file == null || file.isEmpty()) {
            throw AppException.bad("Choose a CSV or Excel (.xlsx) file to upload.");
        }
        List<Map<String, String>> rows = TabularReader.read(file, MAX_ROWS);
        if (rows.isEmpty()) {
            throw AppException.bad("The file contains no data rows.");
        }
        Batch defaultBatch = defaultBatchId == null ? null : batches.findById(defaultBatchId).orElseThrow(() -> AppException.notFound("Batch"));
        Map<String, Batch> batchByCode = new HashMap<>();
        batches.findAll().forEach(b -> batchByCode.put(b.getCode().toLowerCase(Locale.ROOT), b));
        Set<String> seenUsernames = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        List<RowError> errors = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        int rowNo = 1; // header is row 1
        for (Map<String, String> r : rows) {
            rowNo++;
            String username = clean(first(r, "username", "user_name", "login"));
            String fullName = clean(first(r, "full_name", "fullname", "name", "student_name"));
            String email = clean(first(r, "email", "e-mail", "mail"));
            String rollNo = student ? clean(first(r, "roll_no", "rollno", "roll", "roll_number")) : null;
            String phone = clean(first(r, "phone", "mobile", "contact"));
            String pwd = first(r, "password", "pwd");
            String batchCode = student ? clean(first(r, "batch_code", "batch", "batchcode")) : null;
            String err = validate(username, fullName, email, seenUsernames, seenEmails);
            if (err == null && pwd != null && !pwd.isBlank()) {
                err = PasswordPolicy.validate(pwd);
            }
            if (err == null && batchCode != null && !batchByCode.containsKey(batchCode.toLowerCase(Locale.ROOT))) {
                err = "Unknown batch code \'" + batchCode + "\'.";
            }
            if (err != null) {
                errors.add(new RowError(rowNo, err));
                continue;
            }
            boolean generated = pwd == null || pwd.isBlank();
            candidates.add(new Candidate(rowNo, username, fullName, email.toLowerCase(Locale.ROOT), rollNo, phone, generated ? PasswordPolicy.generate() : pwd, generated, batchCode));
        }
        // Existing users in the database (one query each, not one per row).
        Set<String> existingNames = new HashSet<>();
        Set<String> existingEmails = new HashSet<>();
        users.findAll().forEach(u -> {
            existingNames.add(u.getUsername().toLowerCase(Locale.ROOT));
            existingEmails.add(u.getEmail().toLowerCase(Locale.ROOT));
        });
        List<Candidate> ok = new ArrayList<>();
        for (Candidate c : candidates) {
            if (existingNames.contains(c.username().toLowerCase(Locale.ROOT))) {
                errors.add(new RowError(c.row(), "Username \'" + c.username() + "\' already exists."));
            } else if (existingEmails.contains(c.email())) {
                errors.add(new RowError(c.row(), "Email \'" + c.email() + "\' is already registered."));
            } else {
                ok.add(c);
            }
        }
        // BCrypt is deliberately slow: hash in parallel so a 500-row file does not take minutes.
        String[] hashes = new String[ok.size()];
        IntStream.range(0, ok.size()).parallel().forEach(i -> hashes[i] = encoder.encode(ok.get(i).password()));
        List<Credential> created = new ArrayList<>();
        Map<Long, Set<Long>> membership = new HashMap<>();
        for (int from = 0; from < ok.size(); from += 100) {
            int start = from;
            int end = Math.min(from + 100, ok.size());
            tx.executeWithoutResult(status -> {
                for (int i = start; i < end; i++) {
                    Candidate c = ok.get(i);
                    User u = new User();
                    u.setUsername(c.username());
                    u.setEmail(c.email());
                    u.setFullName(c.fullName());
                    u.setRollNo(c.rollNo());
                    u.setPhone(c.phone());
                    u.setPasswordHash(hashes[i]);
                    u.setMustChangePassword(true);
                    u.setRoles(EnumSet.of(role));
                    users.save(u);
                    created.add(new Credential(c.username(), c.fullName(), c.password(), c.generated()));
                    Batch target = c.batchCode() != null ? batchByCode.get(c.batchCode().toLowerCase(Locale.ROOT)) : defaultBatch;
                    if (target != null) {
                        membership.computeIfAbsent(target.getId(), k -> new HashSet<>()).add(u.getId());
                    }
                }
            });
        }
        membership.forEach((batchId, ids) -> tx.executeWithoutResult(s -> {
            Batch b = batches.findById(batchId).orElseThrow();
            b.getStudentIds().addAll(ids);
            batches.save(b);
        }));
        lookup.evictAll();
        errors.sort((a, b) -> Integer.compare(a.row(), b.row()));
        audit.log("USERS_IMPORTED", "User", null, role + ": " + created.size() + " created, " + errors.size() + " rejected");
        return new ImportResult(created, errors, rows.size());
    }

    private static String validate(String username, String fullName, String email, Set<String> seenUsernames, Set<String> seenEmails) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            return "Username is missing or invalid (3-60 chars: letters, digits . _ @ -).";
        }
        if (fullName == null || fullName.length() > 120) {
            return "Full name is missing or too long.";
        }
        if (email == null || email.length() > 160 || !EMAIL.matcher(email).matches()) {
            return "Email is missing or invalid.";
        }
        if (!seenUsernames.add(username.toLowerCase(Locale.ROOT))) {
            return "Username \'" + username + "\' appears more than once in the file.";
        }
        if (!seenEmails.add(email.toLowerCase(Locale.ROOT))) {
            return "Email \'" + email + "\' appears more than once in the file.";
        }
        return null;
    }

    private static String first(Map<String, String> row, String... keys) {
        for (String k : keys) {
            String v = row.get(k);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public UserImportService(final UserRepository users, final BatchRepository batches, final PasswordEncoder encoder, final UserLookupService lookup, final AuditService audit, final TransactionTemplate tx) {
        this.users = users;
        this.batches = batches;
        this.encoder = encoder;
        this.lookup = lookup;
        this.audit = audit;
        this.tx = tx;
    }
}
