ALTER TABLE socialraven.post_collection
    ADD COLUMN review_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN review_submitted_at TIMESTAMPTZ(6),
    ADD COLUMN review_submitted_by VARCHAR(255),
    ADD COLUMN approved_at TIMESTAMPTZ(6),
    ADD COLUMN approved_by VARCHAR(255);

ALTER TABLE socialraven.post_collection
    ADD CONSTRAINT post_collection_review_status_check
        CHECK (review_status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED'));

CREATE INDEX idx_post_collection_review_status
    ON socialraven.post_collection(workspace_id, review_status, review_submitted_at DESC);

CREATE TABLE socialraven.post_collection_review_history (
    id                 BIGSERIAL     PRIMARY KEY,
    post_collection_id BIGINT        NOT NULL REFERENCES socialraven.post_collection(id) ON DELETE CASCADE,
    action             VARCHAR(50)   NOT NULL,
    from_status        VARCHAR(50)   NOT NULL,
    to_status          VARCHAR(50)   NOT NULL,
    actor_user_id      VARCHAR(255)  NOT NULL,
    note               VARCHAR(4000),
    created_at         TIMESTAMPTZ(6) NOT NULL DEFAULT NOW(),
    CONSTRAINT post_collection_review_history_action_check
        CHECK (action IN ('SUBMITTED', 'RESUBMITTED', 'APPROVED', 'CHANGES_REQUESTED')),
    CONSTRAINT post_collection_review_history_from_status_check
        CHECK (from_status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED')),
    CONSTRAINT post_collection_review_history_to_status_check
        CHECK (to_status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED'))
);

CREATE INDEX idx_post_collection_review_history_collection
    ON socialraven.post_collection_review_history(post_collection_id, created_at);
