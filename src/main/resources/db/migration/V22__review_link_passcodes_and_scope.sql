ALTER TABLE socialraven.post_collection_review_link
    ADD COLUMN share_scope VARCHAR(50) NOT NULL DEFAULT 'CAMPAIGN',
    ADD COLUMN shared_post_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN passcode_hash VARCHAR(255);

ALTER TABLE socialraven.post_collection_review_link
    ADD CONSTRAINT post_collection_review_link_share_scope_check
        CHECK (share_scope IN ('CAMPAIGN', 'SELECTED_POSTS'));

ALTER TABLE socialraven.post_collection_review_link
    ADD CONSTRAINT post_collection_review_link_shared_post_ids_type_check
        CHECK (jsonb_typeof(shared_post_ids) = 'array');
