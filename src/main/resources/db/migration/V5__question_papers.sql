-- Uploaded question papers become first-class, named entities that live on their own until the teacher
-- explicitly merges them into the Question Bank (or into another paper). They no longer have to belong to an exam.
ALTER TABLE upload_sessions ALTER COLUMN exam_id DROP NOT NULL;
ALTER TABLE upload_sessions ADD COLUMN subject_id BIGINT REFERENCES subjects (id) ON DELETE CASCADE;
ALTER TABLE upload_sessions ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'DOCX';
ALTER TABLE upload_sessions ADD COLUMN merged_at TIMESTAMPTZ;

UPDATE upload_sessions u SET subject_id = e.subject_id FROM exams e WHERE e.id = u.exam_id AND u.subject_id IS NULL;
ALTER TABLE upload_sessions ALTER COLUMN subject_id SET NOT NULL;

CREATE INDEX ix_upload_sessions_owner ON upload_sessions (created_by, status);
