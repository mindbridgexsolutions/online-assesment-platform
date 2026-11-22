# MindBridgeX Online Assessment Platform

A Moodle-style platform that contains **only** the online-assessment features: question bank, exam builder,
timed exam player, automatic + manual grading, reports and audit.

**Stack:** Java 17 · Spring Boot 3.5 · PostgreSQL 16 + Flyway · JPA · JSP/JSTL + Bootstrap 5 · JWT (HttpOnly cookie) · WAR.

## Roles

| Role    | Can do |
|---------|--------|
| ADMIN   | Users (create / import CSV / deactivate / reset password), batches, subjects, teacher↔subject assignment, all reports, audit log, system settings, IP restrictions |
| TEACHER | Question bank (8 types, categories, import/export, images), exams (manual or random-by-rule), assignment to batches/students, manual grading of essays, reports for own subjects |
| STUDENT | Dashboard of assigned exams, instructions, timed player, results and answer review (per the exam's result policy) |

## Features

- **Question types:** MCQ single, MCQ multiple (optional partial marking), true/false, fill-in-the-blank, short answer (keywords), essay (manual), matching, numeric (with tolerance).
- **Exam builder:** pick questions or random-by-rule (subject / category / difficulty / count), per-exam marks, shuffle questions and options, negative marking, pass mark, attempt limits, time window, duration, IP restriction, result policy (immediate / after window / manual release), assign to batches or students, publish/unpublish.
- **Exam player:** server-authoritative timer, debounced autosave with offline retry, question navigator, flag for review, resume after refresh/crash, auto-submit on time-out (also by a 30 s scheduler), single-session guard, focus-loss logging with optional auto-submit, copy/paste blocking.
- **Integrity:** each attempt is a snapshot of the questions at start time (later edits never change a running/finished attempt); answer keys are never sent to the browser while an attempt is running.
- **Grading:** strategy per question type; negative marks only for wrong non-blank answers; manual grading preserved on regrade; regrade after fixing an answer key.
- **Reports:** score summary, per-question analysis, Excel and PDF export, per-student scorecard PDF.
- **Audit log** for sign-ins, user/exam/question changes, grading and exports.
- **Security:** BCrypt, JWT access (20 min) + rotating refresh (7 d) in HttpOnly/SameSite cookies, instant revocation via `tokenVersion`, CSRF protection, login rate limiting and lockout, strict security headers (CSP without inline scripts, HSTS, frame-deny, nosniff), magic-byte validation for uploads, spreadsheet-formula injection guards, data-level access checks (teachers only see their subjects).

## Quick start (Docker)

```bash
cp .env.example .env
#   edit .env: DB_PASSWORD, APP_JWT_SECRET (openssl rand -base64 48), ADMIN_PASSWORD
#   for plain-HTTP testing set COOKIE_SECURE=false ; to load demo data set SEED_DEMO=true
docker compose up -d --build
```

Open <http://localhost:8080> and sign in as `ADMIN_USERNAME` / `ADMIN_PASSWORD`.
Put a TLS reverse proxy (nginx, Caddy, a cloud load balancer) in front for production and keep `COOKIE_SECURE=true`.

## Run locally in one command (macOS / Linux)

```bash
./run-local.sh
```
Checks Java 17+, creates the PostgreSQL user/database if needed, builds the app and starts it with demo data at
<http://localhost:8080>. The project does not use Lombok, so it opens and compiles in STS / Eclipse / IntelliJ without any
IDE plugin: import it as an existing Maven project (*File > Import > Maven > Existing Maven Projects*) and run
`com.mindbridgex.assessment.AssessmentApplication` as a Spring Boot App with profile `dev` and VM argument `-Duser.timezone=UTC`.

## Run locally (without Docker)

```bash
# PostgreSQL
createuser assess -P            # password: assess_pw
createdb -O assess assessment

mvn -DskipTests package
SPRING_PROFILES_ACTIVE=dev java -jar target/assessment.war
```

The `dev` profile uses `admin` / `Admin@12345` and non-secure cookies. The `dev` profile loads demo data automatically
(set `SEED_DEMO=false` to disable). In Docker set `SEED_DEMO=true` in `.env`. All demo accounts use the password `Demo@12345`:

| Role | Usernames | Notes |
|------|-----------|-------|
| Faculty | `teacher1` | General Knowledge (all 8 question types) |
| Faculty | `teacher2` | Mathematics (MATH101) - 15 MCQs |
| Faculty | `teacher3` | Science (SCI101) - 15 MCQs |
| Students | `student1`-`student5` | BATCH-A |
| Students | `student6`-`student15` | BATCH-B |
| Students | `student16`-`student30` | BATCH-C |

Demo exams (all with negative marking): *General Knowledge - Demo Test* (BATCH-A), *Mathematics MCQ Test* (BATCH-A and B,
results immediate), *Science MCQ Quiz* (BATCH-B and C, results released manually by the teacher), *Mathematics - Upcoming Test*
(opens tomorrow, BATCH-C) and *Science - Draft* (unpublished, for trying the publish workflow). Demo data is only created once,
when the database is empty of it.

To deploy on an external Tomcat 10.1+, use `target/assessment.war` (Jakarta EE 10).

## Uploads

| Screen | Accepted files |
|--------|----------------|
| Question editor - attachment | Images (PNG, JPEG, GIF, WebP, max 2 MB), **PDF** and **Word .docx** (max 10 MB). Images show inline; PDF/Word show as a download link in the exam player and review |
| Question import | `.csv`, `.xlsx` or **Word `.docx`** (data in the first table, column headings in the first row) |
| Student/user import | `.csv`, `.xlsx` or **Word `.docx`** (same table layout) |

File types are decided from the file contents, not the name (a renamed `.exe` or a plain zip called `.docx` is rejected).
PDFs cannot be imported as tables because their columns cannot be read reliably; paste the table into Word, Excel or CSV.

## Configuration (environment variables)

| Variable | Default | Notes |
|----------|---------|-------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | localhost / assess / – | |
| `DB_POOL_SIZE` | 30 | |
| `APP_JWT_SECRET` | – (**required**, ≥ 32 chars) | app refuses to start without it |
| `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | admin / – / – | admin is created on first start only |
| `SEED_DEMO` | false | demo teacher/students/exam |
| `COOKIE_SECURE` | true | set false only on plain HTTP |
| `APP_TIMEZONE` | Asia/Kolkata | timezone used to display and enter dates (stored as UTC) |
| `ATTEMPT_GRACE_SECONDS` | 10 | network grace after the timer ends |
| `STORAGE_DIR` | ./data/uploads | question images – mount a persistent volume |
| `JWT_ACCESS_TTL_MINUTES`, `JWT_REFRESH_TTL_DAYS` | 20 / 7 | |
| `TOMCAT_MAX_THREADS` | 200 | |
| `LOGIN_MAX_FAILURES_PER_IP` | 1000 | failed sign-ins per IP per 15 min (labs share one IP) |

Schema changes are applied automatically by Flyway on start (`src/main/resources/db/migration`).

## Tests

```bash
createdb -O assess assessment_test        # once; integration tests use it
mvn test
```

58 tests: grading rules for every question type, attempt timer / grace / resume / auto-submit,
IP matching, and a full-context security suite (anonymous redirect, tampered/forged tokens, deactivation and
token revocation, role matrix, CSRF, security headers, teacher data isolation). Override the DB with
`TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`. If PostgreSQL is unreachable the integration suite is skipped.

## Load test (1 000 students)

`loadtest/load_test.py` simulates N students: login → everyone starts the exam in the same instant → autosave
(6× each, random answers) → submit → open results. Seed users `load1..loadN` (password `Demo@12345`) into a batch
assigned to an open exam, then run `python3 loadtest/load_test.py 1000 100 6 http://host:8080`.

Measured on a single 2-vCPU / 8 GB machine that was **also** running PostgreSQL and the 1 000-thread load generator
(i.e. worst case): **1 000 / 1 000 students completed, 0 errors**, 6 000 autosaves and 1 000 submits saved correctly,
negative marks applied. Under that CPU saturation p95 latencies were ~6 s for pages and autosave; with 50 students
autosave is ~40 ms. On a dedicated server the numbers are far lower.

Sizing advice for ~1 000 simultaneous candidates: **4 vCPU / 8 GB RAM** for the app, PostgreSQL on separate or
dedicated resources, `DB_POOL_SIZE=30` (default), `TOMCAT_MAX_THREADS=400`. Login is the most CPU-heavy step (BCrypt),
so ask students to sign in 10–15 minutes before the start time; the exam start window (`start_at`) lets them wait on the
dashboard. For safety run two app instances behind a load balancer (the app is stateless).

Lessons already applied: passwords are verified outside DB transactions (so a login burst cannot exhaust the connection
pool), and the per-IP failed-login limit defaults to 1 000 (`LOGIN_MAX_FAILURES_PER_IP`) because a whole exam lab often
shares one public IP — per-account lock-out still protects individual accounts.

## Production checklist

- [ ] Strong, unique `APP_JWT_SECRET`, `DB_PASSWORD`, `ADMIN_PASSWORD`; `.env` not in version control.
- [ ] TLS in front, `COOKIE_SECURE=true`, `SEED_DEMO=false`.
- [ ] Persistent volumes for PostgreSQL and `STORAGE_DIR`; regular `pg_dump` backups tested by restore.
- [ ] Change the admin password after first login; create named admin/teacher accounts.
- [ ] Restrict database network access to the app only.
- [ ] Rotate `APP_JWT_SECRET` to sign everyone out when needed.
- [ ] Monitor `/actuator/health` and the application log.

## Project layout

```
src/main/java/com/mindbridgex/assessment
  config/      security, properties, EL resolver
  security/    JWT, filters, cookies, rate limiting
  domain/      JPA entities        repository/  Spring Data
  service/     business logic (AttemptService, GradingService, ExamService, ReportService ...)
  grading/     grading strategy per question type
  controller/  MVC + JSON endpoints (admin, teacher, student, reports, api)
src/main/webapp/WEB-INF/jsp, tags    JSP views and layout tags
src/main/resources/static            css / js (app.js, question-form.js, player.js)
src/main/resources/db/migration      Flyway SQL
```

## Institution features (v2)

* **Super Administrator** (`/super`): sign in as `superadmin` (dev password `SuperAdmin@12345`; in production set `SUPERADMIN_USERNAME`, `SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD`). Controls admins, teachers, students, branches, approvals (approve/reject exams and questions, always with a reason, audit-logged) and live exam correction.
* **Live correction** (`/super/live`): edit a question or key, accept more than one option, drop a question or give everyone the marks, recalculate submitted scores. The exam keeps running.
* **Duplicates**: checked on every save, CSV/XLSX import and DOCX upload. Cleanup screen: `/admin/duplicates`.
* **DOCX upload** on the exam page: "Upload Questions (.docx)" with a downloadable template; review, edit, then confirm. Used by teachers and the Super Admin (an ADMIN-only account has no teacher area).
* **CO-PO**: Programme Outcomes (`/super/outcomes`), Course Outcomes and mapping (`/teacher/outcomes`), attainment reports per course, class and student with Excel/PDF (`/reports/outcomes`).
* **Delayed results**: choose "On a date and time" when creating an exam.
* Pictures are supported in questions and options (test screen and review).

### Added in v2.1
* Roles **HoD** (approves exams of their branch at `/hod`; set `APPROVAL_REQUIRED=false` to switch the approval step off) and **IQAC** (CO-PO reports only). Demo logins: `hod1`, `iqac1` (password `Demo@12345`).
* Questions carry **Bloom's level** and **Unit**; CO is required once a subject has Course Outcomes. DOCX template columns: Q No, Question, Option A-D, Correct, Marks, CO, Bloom's, Difficulty, Unit. Word equations are read as text, code keeps its line breaks.
* Exam types, "hold results N days", and **Result release control** for the Super Admin (`/super/results`).
* CO coverage and Bloom's coverage on the exam page; Bloom's performance in the CO-PO report (add PSOs as `PSO1`, `PSO2` next to the POs).
* Click a picture to enlarge. Exams run in full screen; leaving it is recorded.

### Demo data for CO-PO (v2.2)
With `SEED_DEMO=true` the app also creates course **DS201 Data Structures**: 3 COs mapped to PO1-PO4, 24 questions tagged with CO, Bloom's level and unit, two exams (Unit Test 1, Mid-Semester) and 20 students `ds01`..`ds20` (password `Demo@12345`) who have already answered them. Open *Reports, CO-PO attainment* and pick Data Structures to see CO, PO, Bloom's and per-student numbers.
