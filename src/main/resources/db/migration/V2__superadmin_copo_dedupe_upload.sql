-- =====================================================================
-- V2: Super Admin, branches, CO-PO mapping, duplicate detection,
--     image options, DOCX upload staging, live-correction flags,
--     scheduled result publishing
-- =====================================================================

-- ---------- Roles & organisation ----------
INSERT INTO roles (code, description) VALUES ('SUPER_ADMIN', 'Super Administrator');

CREATE TABLE branches (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    code       VARCHAR(40)  NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by BIGINT
);

ALTER TABLE users    ADD COLUMN branch_id BIGINT REFERENCES branches (id) ON DELETE SET NULL;
ALTER TABLE subjects ADD COLUMN branch_id BIGINT REFERENCES branches (id) ON DELETE SET NULL;
ALTER TABLE batches  ADD COLUMN branch_id BIGINT REFERENCES branches (id) ON DELETE SET NULL;
CREATE INDEX ix_subjects_branch ON subjects (branch_id);
CREATE INDEX ix_batches_branch  ON batches (branch_id);
CREATE INDEX ix_users_branch    ON users (branch_id);

-- ---------- Outcomes (NBA / NAAC style) ----------
CREATE TABLE course_outcomes (
    id             BIGSERIAL PRIMARY KEY,
    subject_id     BIGINT        NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    code           VARCHAR(20)   NOT NULL,
    description    TEXT,
    target_percent NUMERIC(5,2)  NOT NULL DEFAULT 60 CHECK (target_percent >= 0 AND target_percent <= 100),
    UNIQUE (subject_id, code)
);

CREATE TABLE program_outcomes (
    id          BIGSERIAL PRIMARY KEY,
    branch_id   BIGINT      REFERENCES branches (id) ON DELETE CASCADE,
    code        VARCHAR(20) NOT NULL,
    description TEXT
);
CREATE UNIQUE INDEX ux_po_code ON program_outcomes (COALESCE(branch_id, 0), lower(code));

CREATE TABLE co_po_map (
    co_id BIGINT   NOT NULL REFERENCES course_outcomes (id) ON DELETE CASCADE,
    po_id BIGINT   NOT NULL REFERENCES program_outcomes (id) ON DELETE CASCADE,
    level SMALLINT NOT NULL CHECK (level BETWEEN 1 AND 3),
    PRIMARY KEY (co_id, po_id)
);

-- ---------- Questions: image options, duplicates, CO tag, approval ----------
ALTER TABLE question_options ADD COLUMN image_path VARCHAR(300);

ALTER TABLE questions ADD COLUMN content_hash    VARCHAR(64);
ALTER TABLE questions ADD COLUMN image_hash      VARCHAR(64);
ALTER TABLE questions ADD COLUMN source          VARCHAR(250);
ALTER TABLE questions ADD COLUMN co_id           BIGINT REFERENCES course_outcomes (id) ON DELETE SET NULL;
ALTER TABLE questions ADD COLUMN approval_status VARCHAR(10) NOT NULL DEFAULT 'APPROVED'
    CHECK (approval_status IN ('APPROVED','REJECTED'));
CREATE INDEX ix_questions_hash ON questions (subject_id, content_hash) WHERE archived = FALSE;
CREATE INDEX ix_questions_co   ON questions (co_id);

-- ---------- Exams: scheduled results, approval, live corrections ----------
ALTER TABLE exams DROP CONSTRAINT IF EXISTS exams_result_policy_check;
ALTER TABLE exams ADD CONSTRAINT exams_result_policy_check
    CHECK (result_policy IN ('IMMEDIATE','AFTER_WINDOW','MANUAL','SCHEDULED'));
ALTER TABLE exams ADD COLUMN results_publish_at TIMESTAMPTZ;
ALTER TABLE exams ADD COLUMN approval_status VARCHAR(10) NOT NULL DEFAULT 'APPROVED'
    CHECK (approval_status IN ('APPROVED','REJECTED'));

ALTER TABLE exam_questions ADD COLUMN void_mode VARCHAR(10) NOT NULL DEFAULT 'NONE'
    CHECK (void_mode IN ('NONE','DROPPED','FREE_MARKS'));

ALTER TABLE attempt_questions ADD COLUMN co_id     BIGINT;
ALTER TABLE attempt_questions ADD COLUMN void_mode VARCHAR(10) NOT NULL DEFAULT 'NONE'
    CHECK (void_mode IN ('NONE','DROPPED','FREE_MARKS'));
CREATE INDEX ix_attempt_questions_co ON attempt_questions (co_id);

-- ---------- DOCX upload staging (review before save) ----------
CREATE TABLE upload_sessions (
    id         BIGSERIAL PRIMARY KEY,
    exam_id    BIGINT       NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    file_name  VARCHAR(250) NOT NULL,
    status     VARCHAR(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','CONFIRMED','DISCARDED')),
    created_by BIGINT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_upload_sessions_exam ON upload_sessions (exam_id, status);

CREATE TABLE upload_items (
    id           BIGSERIAL PRIMARY KEY,
    session_id   BIGINT      NOT NULL REFERENCES upload_sessions (id) ON DELETE CASCADE,
    row_no       INT         NOT NULL,
    status       VARCHAR(10) NOT NULL CHECK (status IN ('VALID','DUPLICATE','ERROR')),
    message      VARCHAR(500),
    keep         BOOLEAN     NOT NULL DEFAULT TRUE,
    content_hash VARCHAR(64),
    image_hash   VARCHAR(64),
    payload      TEXT        NOT NULL
);
CREATE INDEX ix_upload_items_session ON upload_items (session_id, row_no);
