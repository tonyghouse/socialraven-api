-- Add DRAFT status to post_collection
ALTER TABLE socialraven.post_collection DROP CONSTRAINT post_collection_status_check;
ALTER TABLE socialraven.post_collection
    ADD CONSTRAINT post_collection_status_check
        CHECK (post_collection_status IN ('DRAFT','SCHEDULED','SUCCESS','PARTIAL_SUCCESS','FAILED'));

-- Add DRAFT status to post
ALTER TABLE socialraven.post DROP CONSTRAINT post_post_status_check;
ALTER TABLE socialraven.post
    ADD CONSTRAINT post_post_status_check
        CHECK (post_status IN ('DRAFT','SCHEDULED','POSTED','FAILED'));
