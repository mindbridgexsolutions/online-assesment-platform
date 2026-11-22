-- =====================================================================
-- Online Assessment Platform - initial schema (PostgreSQL 15+)
-- =====================================================================

-- ---------- Identity & access ----------
CREATE TABLE roles (
    code        VARCHAR(20)  PRIMARY KEY,
    description VARCHAR(100) NOT NULL
);
INSERT INTO roles (code, description) VALUES
    ('ADMIN',   'Administrator'),
    ('TEACHER', 'Teacher / Examiner'),
    ('STUDENT', 'Student');

CREATE TABLE users (
    id                   BIGSERIAL PRIMARY KEY,
    username             VARCHAR(60)  NOT NULL,
    email                VARCHAR(160) NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    full_name            VARCHAR(120) NOT NULL,
    roll_no              VARCHAR(40),
    phone                VARCHAR(30),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_logins        INT          NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    last_login_at        TIMESTAMPTZ,
    token_version        INT          NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           BIGINT
);
CREATE UNIQUE INDEX ux_users_username ON users (lower(username));
CREATE UNIQUE INDEX ux_users_email    ON users (lower(email));
CREATE INDEX ix_users_active          ON users (active);

CREATE TABLE user_roles (
    user_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_code VARCHAR(20) NOT NULL REFERENCES roles (code),
    PRIMARY KEY (user_id, role_code)
);

CREATE TABLE refresh_tokens (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash       VARCHAR(64) NOT NULL UNIQUE,
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked_at       TIMESTAMPTZ,
    replaced_by_hash VARCHAR(64),
    user_agent       VARCHAR(255),
    ip_address       VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);

-- ---------- Organisation ----------
CREATE TABLE batches (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  BIGINT
);

CREATE TABLE batch_students (
    batch_id   BIGINT NOT NULL REFERENCES batches (id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users (id)   ON DELETE CASCADE,
    PRIMARY KEY (batch_id, student_id)
);
CREATE INDEX ix_batch_students_student ON batch_students (student_id);

CREATE TABLE subjects (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    code       VARCHAR(40)  NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by BIGINT
);

CREATE TABLE teacher_subjects (
    teacher_id BIGINT NOT NULL REFERENCES users (id)    ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    PRIMARY KEY (teacher_id, subject_id)
);
CREATE INDEX ix_teacher_subjects_subject ON teacher_subjects (subject_id);

-- ---------- Question bank ----------
CREATE TABLE question_categories (
    id         BIGSERIAL PRIMARY KEY,
    subject_id BIGINT       NOT NULL REFERENCES subjects (id),
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by BIGINT,
    UNIQUE (subject_id, name)
);

CREATE TABLE questions (
    id                BIGSERIAL PRIMARY KEY,
    subject_id        BIGINT        NOT NULL REFERENCES subjects (id),
    category_id       BIGINT        REFERENCES question_categories (id) ON DELETE SET NULL,
    type              VARCHAR(20)   NOT NULL
        CHECK (type IN ('MCQ_SINGLE','MCQ_MULTI','TRUE_FALSE','FILL_BLANK','SHORT_ANSWER','ESSAY','MATCHING','NUMERIC')),
    difficulty        VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM'
        CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    text              TEXT          NOT NULL,
    image_path        VARCHAR(300),
    explanation       TEXT,
    default_marks     NUMERIC(6,2)  NOT NULL DEFAULT 1 CHECK (default_marks > 0),
    partial_marking   BOOLEAN       NOT NULL DEFAULT FALSE,
    case_sensitive    BOOLEAN       NOT NULL DEFAULT FALSE,
    numeric_answer    NUMERIC(20,6),
    numeric_tolerance NUMERIC(20,6) NOT NULL DEFAULT 0 CHECK (numeric_tolerance >= 0),
    archived          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        BIGINT
);
CREATE INDEX ix_questions_subject    ON questions (subject_id, archived);
CREATE INDEX ix_questions_category   ON questions (category_id);
CREATE INDEX ix_questions_difficulty ON questions (subject_id, difficulty);

CREATE TABLE question_options (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT  NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    position    INT     NOT NULL,
    text        TEXT    NOT NULL,
    match_text  TEXT,
    correct     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_question_options_question ON question_options (question_id, position);

CREATE TABLE question_tags (
    question_id BIGINT      NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    tag         VARCHAR(40) NOT NULL,
    PRIMARY KEY (question_id, tag)
);
CREATE INDEX ix_question_tags_tag ON question_tags (tag);

-- ---------- Exams ----------
CREATE TABLE exams (
    id                BIGSERIAL PRIMARY KEY,
    subject_id        BIGINT       NOT NULL REFERENCES subjects (id),
    title             VARCHAR(200) NOT NULL,
    instructions      TEXT,
    duration_minutes  INT          NOT NULL CHECK (duration_minutes > 0),
    start_at          TIMESTAMPTZ  NOT NULL,
    end_at            TIMESTAMPTZ  NOT NULL,
    max_attempts      INT          NOT NULL DEFAULT 1 CHECK (max_attempts > 0),
    passing_marks     NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (passing_marks >= 0),
    negative_marking  BOOLEAN      NOT NULL DEFAULT FALSE,
    negative_fraction NUMERIC(4,2) NOT NULL DEFAULT 0.25 CHECK (negative_fraction >= 0 AND negative_fraction <= 1),
    shuffle_questions BOOLEAN      NOT NULL DEFAULT TRUE,
    shuffle_options   BOOLEAN      NOT NULL DEFAULT TRUE,
    result_policy     VARCHAR(20)  NOT NULL DEFAULT 'AFTER_WINDOW'
        CHECK (result_policy IN ('IMMEDIATE','AFTER_WINDOW','MANUAL')),
    results_released  BOOLEAN      NOT NULL DEFAULT FALSE,
    review_allowed    BOOLEAN      NOT NULL DEFAULT TRUE,
    status            VARCHAR(12)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    allowed_ips       TEXT,
    max_focus_losses  INT          NOT NULL DEFAULT 0 CHECK (max_focus_losses >= 0),
    block_copy_paste  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        BIGINT,
    CHECK (end_at > start_at)
);
CREATE INDEX ix_exams_subject ON exams (subject_id);
CREATE INDEX ix_exams_owner   ON exams (created_by, status);
CREATE INDEX ix_exams_window  ON exams (status, start_at, end_at);

CREATE TABLE exam_question_rules (
    id                 BIGSERIAL PRIMARY KEY,
    exam_id            BIGINT       NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    category_id        BIGINT       REFERENCES question_categories (id) ON DELETE CASCADE,
    difficulty         VARCHAR(10)  CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    question_count     INT          NOT NULL CHECK (question_count > 0),
    marks_per_question NUMERIC(6,2) CHECK (marks_per_question > 0)
);
CREATE INDEX ix_exam_rules_exam ON exam_question_rules (exam_id);

CREATE TABLE exam_questions (
    id          BIGSERIAL PRIMARY KEY,
    exam_id     BIGINT       NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    question_id BIGINT       NOT NULL REFERENCES questions (id),
    marks       NUMERIC(6,2) NOT NULL CHECK (marks > 0),
    position    INT          NOT NULL DEFAULT 0,
    UNIQUE (exam_id, question_id)
);
CREATE INDEX ix_exam_questions_question ON exam_questions (question_id);

CREATE TABLE exam_assignments (
    id         BIGSERIAL PRIMARY KEY,
    exam_id    BIGINT NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    batch_id   BIGINT REFERENCES batches (id) ON DELETE CASCADE,
    student_id BIGINT REFERENCES users (id)   ON DELETE CASCADE,
    CHECK ((batch_id IS NULL) <> (student_id IS NULL))
);
CREATE UNIQUE INDEX ux_exam_assign_batch   ON exam_assignments (exam_id, batch_id)   WHERE batch_id   IS NOT NULL;
CREATE UNIQUE INDEX ux_exam_assign_student ON exam_assignments (exam_id, student_id) WHERE student_id IS NOT NULL;
CREATE INDEX ix_exam_assign_batch   ON exam_assignments (batch_id);
CREATE INDEX ix_exam_assign_student ON exam_assignments (student_id);

-- ---------- Attempts (snapshot-based) ----------
CREATE TABLE attempts (
    id               BIGSERIAL PRIMARY KEY,
    exam_id          BIGINT      NOT NULL REFERENCES exams (id),
    student_id       BIGINT      NOT NULL REFERENCES users (id),
    attempt_no       INT         NOT NULL,
    status           VARCHAR(12) NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (status IN ('IN_PROGRESS','SUBMITTED','GRADED')),
    started_at       TIMESTAMPTZ NOT NULL,
    ends_at          TIMESTAMPTZ NOT NULL,
    submitted_at     TIMESTAMPTZ,
    submit_reason    VARCHAR(12) CHECK (submit_reason IN ('STUDENT','TIMEOUT','VIOLATION','TEACHER')),
    player_session   VARCHAR(64),
    focus_loss_count INT         NOT NULL DEFAULT 0,
    ip_address       VARCHAR(64),
    user_agent       VARCHAR(255),
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       BIGINT,
    UNIQUE (exam_id, student_id, attempt_no)
);
-- A student can never have two active attempts for the same exam (race-proof).
CREATE UNIQUE INDEX ux_attempts_active      ON attempts (exam_id, student_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_attempts_exam_student       ON attempts (exam_id, student_id);
CREATE INDEX ix_attempts_student            ON attempts (student_id);
CREATE INDEX ix_attempts_expiry             ON attempts (ends_at) WHERE status = 'IN_PROGRESS';

CREATE TABLE attempt_questions (
    id                 BIGSERIAL PRIMARY KEY,
    attempt_id         BIGINT       NOT NULL REFERENCES attempts (id) ON DELETE CASCADE,
    position           INT          NOT NULL,
    source_question_id BIGINT,
    type               VARCHAR(20)  NOT NULL,
    text               TEXT         NOT NULL,
    image_path         VARCHAR(300),
    explanation        TEXT,
    marks              NUMERIC(6,2) NOT NULL,
    negative_marks     NUMERIC(6,2) NOT NULL DEFAULT 0,
    partial_marking    BOOLEAN      NOT NULL DEFAULT FALSE,
    case_sensitive     BOOLEAN      NOT NULL DEFAULT FALSE,
    numeric_answer     NUMERIC(20,6),
    numeric_tolerance  NUMERIC(20,6) NOT NULL DEFAULT 0,
    options            JSONB        NOT NULL DEFAULT '[]'::jsonb,
    UNIQUE (attempt_id, position)
);
CREATE INDEX ix_attempt_questions_source ON attempt_questions (source_question_id);

CREATE TABLE attempt_answers (
    id                  BIGSERIAL PRIMARY KEY,
    attempt_id          BIGINT       NOT NULL REFERENCES attempts (id) ON DELETE CASCADE,
    attempt_question_id BIGINT       NOT NULL REFERENCES attempt_questions (id) ON DELETE CASCADE,
    answer              TEXT,
    flagged             BOOLEAN      NOT NULL DEFAULT FALSE,
    answered_at         TIMESTAMPTZ,
    awarded_marks       NUMERIC(8,2),
    correct             BOOLEAN,
    needs_manual        BOOLEAN      NOT NULL DEFAULT FALSE,
    feedback            TEXT,
    graded_by           BIGINT,
    graded_at           TIMESTAMPTZ,
    UNIQUE (attempt_id, attempt_question_id)
);
CREATE UNIQUE INDEX ux_attempt_answers_question ON attempt_answers (attempt_question_id);

CREATE TABLE grades (
    id             BIGSERIAL PRIMARY KEY,
    attempt_id     BIGINT        NOT NULL UNIQUE REFERENCES attempts (id) ON DELETE CASCADE,
    total_score    NUMERIC(8,2)  NOT NULL,
    max_score      NUMERIC(8,2)  NOT NULL,
    percentage     NUMERIC(6,2)  NOT NULL,
    passed         BOOLEAN       NOT NULL,
    pending_manual INT           NOT NULL DEFAULT 0,
    graded_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------- Audit ----------
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT,
    actor_name  VARCHAR(120),
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(40),
    entity_id   VARCHAR(40),
    details     TEXT,
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_created ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_action  ON audit_logs (action, created_at DESC);
CREATE INDEX ix_audit_actor   ON audit_logs (actor_id, created_at DESC);
