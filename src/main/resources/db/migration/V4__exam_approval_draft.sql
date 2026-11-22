ALTER TABLE exams DROP CONSTRAINT IF EXISTS exams_approval_status_check;
ALTER TABLE exams ADD CONSTRAINT exams_approval_status_check
  CHECK (approval_status IN ('DRAFT','PENDING','APPROVED','REJECTED'));
