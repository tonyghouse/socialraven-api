CREATE TABLE socialraven.post_collection_review_link (
    id                  VARCHAR(36)    PRIMARY KEY,
    post_collection_id  BIGINT         NOT NULL REFERENCES socialraven.post_collection(id) ON DELETE CASCADE,
    workspace_id        VARCHAR(255)   NOT NULL REFERENCES socialraven.workspace(id) ON DELETE CASCADE,
    created_by_user_id  VARCHAR(255)   NOT NULL,
    revoked_by_user_id  VARCHAR(255),
    expires_at          TIMESTAMPTZ(6) NOT NULL,
    revoked_at          TIMESTAMPTZ(6),
    last_accessed_at    TIMESTAMPTZ(6),
    created_at          TIMESTAMPTZ(6) NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_post_collection_review_link_lookup
    ON socialraven.post_collection_review_link(post_collection_id, revoked_at, expires_at DESC);

ALTER TABLE socialraven.post_collection_collaboration_thread
    ADD COLUMN author_type VARCHAR(50) NOT NULL DEFAULT 'WORKSPACE_USER',
    ADD COLUMN author_display_name VARCHAR(255),
    ADD COLUMN author_email VARCHAR(320);

ALTER TABLE socialraven.post_collection_collaboration_thread
    ALTER COLUMN author_user_id DROP NOT NULL;

ALTER TABLE socialraven.post_collection_collaboration_thread
    ADD CONSTRAINT post_collection_collaboration_author_type_check
        CHECK (author_type IN ('WORKSPACE_USER', 'CLIENT_REVIEWER'));

ALTER TABLE socialraven.post_collection_review_history
    ADD COLUMN actor_type VARCHAR(50) NOT NULL DEFAULT 'WORKSPACE_USER',
    ADD COLUMN actor_display_name VARCHAR(255),
    ADD COLUMN actor_email VARCHAR(320);

ALTER TABLE socialraven.post_collection_review_history
    ALTER COLUMN actor_user_id DROP NOT NULL;

ALTER TABLE socialraven.post_collection_review_history
    ADD CONSTRAINT post_collection_review_history_actor_type_check
        CHECK (actor_type IN ('WORKSPACE_USER', 'CLIENT_REVIEWER'));
