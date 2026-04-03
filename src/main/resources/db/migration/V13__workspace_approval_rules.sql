ALTER TABLE socialraven.workspace
    ADD COLUMN approval_mode VARCHAR(50) NOT NULL DEFAULT 'OPTIONAL';

ALTER TABLE socialraven.workspace
    ADD CONSTRAINT workspace_approval_mode_check
        CHECK (approval_mode IN ('NONE', 'OPTIONAL', 'REQUIRED', 'MULTI_STEP'));

CREATE TABLE socialraven.workspace_user_capability (
    id          BIGSERIAL    PRIMARY KEY,
    workspace_id VARCHAR(255) NOT NULL REFERENCES socialraven.workspace(id) ON DELETE CASCADE,
    user_id     VARCHAR(255) NOT NULL,
    capability  VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT workspace_user_capability_unique
        UNIQUE (workspace_id, user_id, capability),
    CONSTRAINT workspace_user_capability_check
        CHECK (capability IN (
            'APPROVE_POSTS',
            'PUBLISH_POSTS',
            'REQUEST_CHANGES',
            'MANAGE_APPROVAL_RULES',
            'SHARE_REVIEW_LINKS',
            'MANAGE_ASSET_LIBRARY',
            'EXPORT_CLIENT_REPORTS'
        ))
);

CREATE INDEX idx_workspace_user_capability_lookup
    ON socialraven.workspace_user_capability(workspace_id, user_id, capability);

ALTER TABLE socialraven.post_collection
    ADD COLUMN required_approval_steps INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN completed_approval_steps INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_approval_stage VARCHAR(50);

ALTER TABLE socialraven.post_collection
    ADD CONSTRAINT post_collection_approval_steps_check
        CHECK (
            required_approval_steps >= 0
            AND completed_approval_steps >= 0
            AND completed_approval_steps <= required_approval_steps
        ),
    ADD CONSTRAINT post_collection_next_approval_stage_check
        CHECK (next_approval_stage IN ('APPROVER', 'OWNER_FINAL') OR next_approval_stage IS NULL);

UPDATE socialraven.post_collection
SET required_approval_steps = CASE
        WHEN review_status = 'IN_REVIEW' THEN 1
        WHEN review_status = 'APPROVED' THEN 1
        ELSE 0
    END,
    completed_approval_steps = CASE
        WHEN review_status = 'APPROVED' THEN 1
        ELSE 0
    END,
    next_approval_stage = CASE
        WHEN review_status = 'IN_REVIEW' THEN 'APPROVER'
        ELSE NULL
    END;

ALTER TABLE socialraven.post_collection_review_history
    DROP CONSTRAINT post_collection_review_history_action_check;

ALTER TABLE socialraven.post_collection_review_history
    ADD CONSTRAINT post_collection_review_history_action_check
        CHECK (action IN ('SUBMITTED', 'RESUBMITTED', 'STEP_APPROVED', 'APPROVED', 'CHANGES_REQUESTED'));
