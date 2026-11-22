-- V3: Bloom's level and unit on questions, HoD / IQAC roles, exam approval workflow, result hold in days
INSERT INTO roles (code, description) VALUES ('HOD', 'Head of Department');
INSERT INTO roles (code, description) VALUES ('IQAC', 'IQAC / NBA Coordinator');

ALTER TABLE questions ADD COLUMN bloom VARCHAR(12)
    CHECK (bloom IS NULL OR bloom IN ('REMEMBER','UNDERSTAND','APPLY','ANALYZE','EVALUATE','CREATE'));
ALTER TABLE questions ADD COLUMN unit VARCHAR(80);

ALTER TABLE exams DROP CONSTRAINT IF EXISTS exams_approval_status_check;
ALTER TABLE exams ADD CONSTRAINT exams_approval_status_check CHECK (approval_status IN ('APPROVED','REJECTED','PENDING'));
ALTER TABLE exams ADD COLUMN exam_type VARCHAR(20) NOT NULL DEFAULT 'INTERNAL';
ALTER TABLE exams ADD COLUMN result_delay_days INT;
