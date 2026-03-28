-- Rename post_status value POSTED → PUBLISHED for consistency with UI and API naming.

ALTER TABLE socialraven.post DROP CONSTRAINT post_post_status_check;

UPDATE socialraven.post SET post_status = 'PUBLISHED' WHERE post_status = 'POSTED';

ALTER TABLE socialraven.post
    ADD CONSTRAINT post_post_status_check
        CHECK (post_status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'FAILED'));
