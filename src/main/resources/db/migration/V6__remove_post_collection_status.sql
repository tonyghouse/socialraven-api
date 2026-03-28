-- Replace post_collection_status VARCHAR column with a simple is_draft boolean.
-- The status of a collection is now derived entirely from individual post statuses.

ALTER TABLE socialraven.post_collection
    ADD COLUMN is_draft BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE socialraven.post_collection
    SET is_draft = TRUE
    WHERE post_collection_status = 'DRAFT';

ALTER TABLE socialraven.post_collection
    DROP CONSTRAINT post_collection_status_check;

ALTER TABLE socialraven.post_collection
    DROP COLUMN post_collection_status;
