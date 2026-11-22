-- Review dashboard: a teacher can leave any question of a paper out of the merge (independent of validation and duplicate status).
ALTER TABLE upload_items ADD COLUMN excluded BOOLEAN NOT NULL DEFAULT FALSE;
