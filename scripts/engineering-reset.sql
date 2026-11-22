-- MindBridgeX Assess: reset the academic data to an engineering-college set-up (LOCAL / TEST DATABASES ONLY).
--
-- WHAT IT DELETES: every question, question category, uploaded paper, exam, attempt and grade, plus the old demo
--                  subjects, batches, branches, course outcomes and programme outcomes.
-- WHAT IT KEEPS:   all users (admin, superadmin, teachers, students) and the audit log.
-- WHAT IT ADDS:    8 B.Tech branches, 4 sample subjects per branch (CO1-CO3 each), and gives every teacher access to them.
--                  The subject names are typical engineering subjects for each branch. They are NOT SAKEC's official
--                  syllabus; replace them with the real list when you have it.
--
-- HOW TO RUN (stop the app first, take a backup first):
--   pg_dump -U assess -h localhost assessment > backup-before-reset.sql
--   psql -U assess -h localhost assessment -f scripts/engineering-reset.sql
-- Everything runs in ONE transaction: if any statement fails, nothing is changed.
-- Never run this on the live server unless you really want to remove its data.

BEGIN;

DELETE FROM attempts;                 -- attempt questions, answers and grades go with them
DELETE FROM exams;                    -- exam rules, questions and assignments go with them
DELETE FROM upload_sessions;          -- staged papers and their rows
DELETE FROM questions;                -- options and tags go with them
DELETE FROM question_categories;
DELETE FROM batches;                  -- batch memberships go with them
DELETE FROM subjects;                 -- course outcomes, teacher assignments and CO-PO links go with them
DELETE FROM program_outcomes;
DELETE FROM branches;                 -- users keep their account; their branch link is cleared


INSERT INTO branches (name, code) VALUES ('Computer Engineering', 'CE');
INSERT INTO branches (name, code) VALUES ('Information Technology', 'IT');
INSERT INTO branches (name, code) VALUES ('Electronics & Telecommunication Engineering', 'EXTC');
INSERT INTO branches (name, code) VALUES ('Artificial Intelligence & Data Science', 'ADS');
INSERT INTO branches (name, code) VALUES ('Cyber Security', 'CYSE');
INSERT INTO branches (name, code) VALUES ('Electronics & Computer Science', 'ECS');
INSERT INTO branches (name, code) VALUES ('Electronics Engineering (VLSI Design & Technology)', 'VLSI');
INSERT INTO branches (name, code) VALUES ('Electronics & Communication (Advanced Communication Technology)', 'ACT');

INSERT INTO subjects (name, code, branch_id) SELECT 'Data Structures', 'CE-DS', id FROM branches WHERE code = 'CE';
INSERT INTO subjects (name, code, branch_id) SELECT 'Operating Systems', 'CE-OS', id FROM branches WHERE code = 'CE';
INSERT INTO subjects (name, code, branch_id) SELECT 'Database Management Systems', 'CE-DBMS', id FROM branches WHERE code = 'CE';
INSERT INTO subjects (name, code, branch_id) SELECT 'Computer Networks', 'CE-CN', id FROM branches WHERE code = 'CE';
INSERT INTO subjects (name, code, branch_id) SELECT 'Web Technologies', 'IT-WT', id FROM branches WHERE code = 'IT';
INSERT INTO subjects (name, code, branch_id) SELECT 'Software Engineering', 'IT-SE', id FROM branches WHERE code = 'IT';
INSERT INTO subjects (name, code, branch_id) SELECT 'Cloud Computing', 'IT-CC', id FROM branches WHERE code = 'IT';
INSERT INTO subjects (name, code, branch_id) SELECT 'Information Security', 'IT-IS', id FROM branches WHERE code = 'IT';
INSERT INTO subjects (name, code, branch_id) SELECT 'Digital Signal Processing', 'EXTC-DSP', id FROM branches WHERE code = 'EXTC';
INSERT INTO subjects (name, code, branch_id) SELECT 'Analog Communication', 'EXTC-AC', id FROM branches WHERE code = 'EXTC';
INSERT INTO subjects (name, code, branch_id) SELECT 'Microwave Engineering', 'EXTC-MW', id FROM branches WHERE code = 'EXTC';
INSERT INTO subjects (name, code, branch_id) SELECT 'Electromagnetic Waves', 'EXTC-EMW', id FROM branches WHERE code = 'EXTC';
INSERT INTO subjects (name, code, branch_id) SELECT 'Machine Learning', 'ADS-ML', id FROM branches WHERE code = 'ADS';
INSERT INTO subjects (name, code, branch_id) SELECT 'Data Warehousing & Mining', 'ADS-DWM', id FROM branches WHERE code = 'ADS';
INSERT INTO subjects (name, code, branch_id) SELECT 'Statistics for Data Science', 'ADS-STAT', id FROM branches WHERE code = 'ADS';
INSERT INTO subjects (name, code, branch_id) SELECT 'Big Data Analytics', 'ADS-BDA', id FROM branches WHERE code = 'ADS';
INSERT INTO subjects (name, code, branch_id) SELECT 'Network Security', 'CYSE-NS', id FROM branches WHERE code = 'CYSE';
INSERT INTO subjects (name, code, branch_id) SELECT 'Cryptography', 'CYSE-CRY', id FROM branches WHERE code = 'CYSE';
INSERT INTO subjects (name, code, branch_id) SELECT 'Ethical Hacking', 'CYSE-EH', id FROM branches WHERE code = 'CYSE';
INSERT INTO subjects (name, code, branch_id) SELECT 'Digital Forensics', 'CYSE-DF', id FROM branches WHERE code = 'CYSE';
INSERT INTO subjects (name, code, branch_id) SELECT 'Embedded Systems', 'ECS-ES', id FROM branches WHERE code = 'ECS';
INSERT INTO subjects (name, code, branch_id) SELECT 'Microprocessors', 'ECS-MP', id FROM branches WHERE code = 'ECS';
INSERT INTO subjects (name, code, branch_id) SELECT 'Digital Logic Design', 'ECS-DLD', id FROM branches WHERE code = 'ECS';
INSERT INTO subjects (name, code, branch_id) SELECT 'Control Systems', 'ECS-CS', id FROM branches WHERE code = 'ECS';
INSERT INTO subjects (name, code, branch_id) SELECT 'Digital VLSI Design', 'VLSI-DVD', id FROM branches WHERE code = 'VLSI';
INSERT INTO subjects (name, code, branch_id) SELECT 'Analog CMOS Design', 'VLSI-ACD', id FROM branches WHERE code = 'VLSI';
INSERT INTO subjects (name, code, branch_id) SELECT 'Semiconductor Devices', 'VLSI-SD', id FROM branches WHERE code = 'VLSI';
INSERT INTO subjects (name, code, branch_id) SELECT 'Hardware Description Languages', 'VLSI-HDL', id FROM branches WHERE code = 'VLSI';
INSERT INTO subjects (name, code, branch_id) SELECT 'Wireless Communication', 'ACT-WC', id FROM branches WHERE code = 'ACT';
INSERT INTO subjects (name, code, branch_id) SELECT 'Antenna & Wave Propagation', 'ACT-AWP', id FROM branches WHERE code = 'ACT';
INSERT INTO subjects (name, code, branch_id) SELECT 'Optical Communication', 'ACT-OC', id FROM branches WHERE code = 'ACT';
INSERT INTO subjects (name, code, branch_id) SELECT 'Satellite Communication', 'ACT-SC', id FROM branches WHERE code = 'ACT';

INSERT INTO course_outcomes (subject_id, code, description)
SELECT s.id, co.code, co.code || ' of ' || s.name FROM subjects s CROSS JOIN (VALUES ('CO1'), ('CO2'), ('CO3')) AS co(code);

INSERT INTO teacher_subjects (teacher_id, subject_id)
SELECT ur.user_id, s.id FROM user_roles ur CROSS JOIN subjects s WHERE ur.role_code = 'TEACHER'
ON CONFLICT DO NOTHING;

COMMIT;
