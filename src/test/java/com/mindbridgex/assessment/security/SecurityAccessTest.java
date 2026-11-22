package com.mindbridgex.assessment.security;

import com.mindbridgex.assessment.domain.*;
import com.mindbridgex.assessment.entity.*;
import com.mindbridgex.assessment.repository.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Role-based access, CSRF and token-invalidation rules against the real security filter chain and a real PostgreSQL
 * database (TEST_DB_URL, default: local database "assessment_test"). Skipped automatically when no database is reachable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAccessTest {

    @BeforeAll
    static void requireDatabase() {
        String url = System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5432/assessment_test");
        String user = System.getenv().getOrDefault("TEST_DB_USERNAME", "assess");
        String pw = System.getenv().getOrDefault("TEST_DB_PASSWORD", "assess_pw");
        boolean ok;
        try (Connection c = DriverManager.getConnection(url, user, pw)) {
            ok = c.isValid(2);
        } catch (Exception e) {
            ok = false;
        }
        if (!ok && "true".equalsIgnoreCase(System.getenv("REQUIRE_DB"))) {
            org.junit.jupiter.api.Assertions.fail("REQUIRE_DB=true but the PostgreSQL test database is not reachable");
        }
        assumeTrue(ok, "PostgreSQL test database not reachable - skipping integration tests");
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SubjectRepository subjects;
    @Autowired ExamRepository exams;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired UserLookupService lookup;

    User admin, teacher, otherTeacher, student, otherStudent;
    Subject mySubject, foreignSubject;
    Exam foreignExam;

    @BeforeEach
    void data() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        admin = user("adm" + tag, RoleType.ADMIN);
        teacher = user("tea" + tag, RoleType.TEACHER);
        otherTeacher = user("tea2" + tag, RoleType.TEACHER);
        student = user("stu" + tag, RoleType.STUDENT);
        otherStudent = user("stu2" + tag, RoleType.STUDENT);
        mySubject = subject("MY" + tag, teacher);
        foreignSubject = subject("FX" + tag, otherTeacher);
        foreignExam = new Exam();
        foreignExam.setSubject(foreignSubject);
        foreignExam.setTitle("Foreign exam");
        foreignExam.setStartAt(Instant.now());
        foreignExam.setEndAt(Instant.now().plusSeconds(3600));
        foreignExam = exams.save(foreignExam);
        lookup.evictAll();
    }

    private User user(String name, RoleType role) {
        User u = new User();
        u.setUsername(name);
        u.setFullName(name);
        u.setEmail(name + "@example.com");
        u.setPasswordHash(encoder.encode("Test@12345"));
        u.setRoles(EnumSet.of(role));
        return users.save(u);
    }

    private Subject subject(String code, User teacher) {
        Subject s = new Subject();
        s.setName(code);
        s.setCode(code);
        s.getTeacherIds().add(teacher.getId());
        return subjects.save(s);
    }

    private RequestPostProcessor as(User u) {
        return req -> {
            req.setCookies(new Cookie(CookieUtil.ACCESS_COOKIE, jwt.createAccessToken(u)));
            return req;
        };
    }

    // ------------------------------------------------------------------ authentication

    @Test
    void anonymousUsersAreSentToTheLoginPage() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?next=%2Fadmin"));
        mvc.perform(get("/student")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/teacher/questions")).andExpect(status().is3xxRedirection());
    }

    @Test
    void apiCallsWithoutLoginGetJson401() throws Exception {
        mvc.perform(post("/api/attempts/1/answers").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("unauthenticated"));
    }

    @Test
    void loginPageAndHealthArePublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void forgedOrGarbageTokensAreTreatedAsAnonymous() throws Exception {
        mvc.perform(get("/admin").cookie(new Cookie(CookieUtil.ACCESS_COOKIE, "garbage"))).andExpect(status().is3xxRedirection());
        String token = jwt.createAccessToken(admin);
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        mvc.perform(get("/admin").cookie(new Cookie(CookieUtil.ACCESS_COOKIE, tampered))).andExpect(status().is3xxRedirection());
    }

    @Test
    void tokenStopsWorkingAfterTheAccountIsDeactivated() throws Exception {
        mvc.perform(get("/student").with(as(student))).andExpect(status().isOk());
        User u = users.findById(student.getId()).orElseThrow();
        u.setActive(false);
        users.save(u);
        lookup.evictAll();
        mvc.perform(get("/student").with(as(student))).andExpect(status().is3xxRedirection());
    }

    @Test
    void tokenStopsWorkingAfterThePasswordIsChanged() throws Exception {
        String old = jwt.createAccessToken(teacher);
        mvc.perform(get("/teacher").cookie(new Cookie(CookieUtil.ACCESS_COOKIE, old))).andExpect(status().isOk());
        User u = users.findById(teacher.getId()).orElseThrow();
        u.setTokenVersion(u.getTokenVersion() + 1);
        users.save(u);
        lookup.evictAll();
        mvc.perform(get("/teacher").cookie(new Cookie(CookieUtil.ACCESS_COOKIE, old))).andExpect(status().is3xxRedirection());
    }

    // ------------------------------------------------------------------ roles

    @Test
    void studentsCannotReachStaffAreas() throws Exception {
        mvc.perform(get("/student").with(as(student))).andExpect(status().isOk());
        mvc.perform(get("/admin").with(as(student))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users").with(as(student))).andExpect(status().isForbidden());
        mvc.perform(get("/teacher").with(as(student))).andExpect(status().isForbidden());
        mvc.perform(get("/teacher/questions/new").with(as(student))).andExpect(status().isForbidden());
        mvc.perform(get("/reports").with(as(student))).andExpect(status().isForbidden());
    }

    @Test
    void teachersCannotReachAdminOrStudentAreas() throws Exception {
        mvc.perform(get("/teacher").with(as(teacher))).andExpect(status().isOk());
        mvc.perform(get("/reports").with(as(teacher))).andExpect(status().isOk());
        mvc.perform(get("/admin").with(as(teacher))).andExpect(status().isForbidden());
        mvc.perform(get("/student").with(as(teacher))).andExpect(status().isForbidden());
        mvc.perform(post("/api/attempts/1/focus-lost").with(as(teacher)).with(csrf()).header("X-Player-Session", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminsManageUsersButDoNotUseTheStudentOrTeacherAreas() throws Exception {
        mvc.perform(get("/admin").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/users").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get("/reports").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get("/teacher").with(as(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/student").with(as(admin))).andExpect(status().isForbidden());
    }

    @Test
    void securityHeadersAreSent() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    // ------------------------------------------------------------------ CSRF

    @Test
    void stateChangingRequestsNeedACsrfToken() throws Exception {
        mvc.perform(post("/admin/subjects/" + mySubject.getId() + "/toggle-active").with(as(admin)).param("active", "false"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/subjects/" + mySubject.getId() + "/toggle-active").with(as(admin)).with(csrf()).param("active", "false"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/auth/logout")).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ data-level access

    @Test
    void teachersOnlySeeExamsOfTheirOwnSubjects() throws Exception {
        mvc.perform(get("/teacher/exams/" + foreignExam.getId()).with(as(teacher))).andExpect(status().isForbidden());
        mvc.perform(get("/teacher/exams/" + foreignExam.getId() + "/edit").with(as(teacher))).andExpect(status().isForbidden());
        mvc.perform(get("/reports/exams/" + foreignExam.getId()).with(as(teacher))).andExpect(status().isForbidden());
        mvc.perform(post("/teacher/exams/" + foreignExam.getId() + "/publish").with(as(teacher)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/teacher/exams/" + foreignExam.getId()).with(as(otherTeacher))).andExpect(status().isOk());
        mvc.perform(get("/reports/exams/" + foreignExam.getId()).with(as(admin))).andExpect(status().isOk());
    }

    @Test
    void studentsCannotOpenOrWriteToSomeoneElsesAttempt() throws Exception {
        mvc.perform(get("/student/attempts/999999").with(as(student))).andExpect(status().isNotFound());
        mvc.perform(post("/api/attempts/999999/answers").with(as(student)).with(csrf()).header("X-Player-Session", "x")
                .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[]}")).andExpect(status().isNotFound());
        mvc.perform(get("/student/results/999999").with(as(student))).andExpect(status().isNotFound());
    }

    @Test
    void studentsCannotStartExamsAssignedToNobodyTheyBelongTo() throws Exception {
        mvc.perform(post("/student/exams/" + foreignExam.getId() + "/start").with(as(student)).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
