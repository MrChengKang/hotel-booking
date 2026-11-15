-- Preserve publication/booking state while adding a separate review lifecycle.
ALTER TABLE stay_hotels ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED';
ALTER TABLE stay_hotels ADD COLUMN review_note VARCHAR(1000) NULL;
ALTER TABLE stay_hotels ADD COLUMN submitted_at DATETIME(6) NULL;
ALTER TABLE stay_hotels ADD COLUMN reviewed_at DATETIME(6) NULL;
ALTER TABLE stay_hotels ADD COLUMN reviewed_by_id BIGINT NULL;
UPDATE stay_hotels SET review_status='APPROVED' WHERE status='PUBLISHED' AND review_status='NOT_SUBMITTED';
CREATE INDEX ix_stay_hotels_review ON stay_hotels(review_status,id);
