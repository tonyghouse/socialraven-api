ALTER TABLE socialraven.post_collection
    ADD COLUMN approval_locked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN approval_locked_at TIMESTAMPTZ(6),
    ADD COLUMN version_sequence INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_approved_version_id BIGINT,
    ADD COLUMN approval_reminder_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_approval_reminder_sent_at TIMESTAMPTZ(6),
    ADD COLUMN next_approval_reminder_at TIMESTAMPTZ(6),
    ADD COLUMN approval_escalated_at TIMESTAMPTZ(6);

ALTER TABLE socialraven.post_collection
    ADD CONSTRAINT post_collection_version_sequence_check
        CHECK (version_sequence >= 0),
    ADD CONSTRAINT post_collection_approval_reminder_attempt_count_check
        CHECK (approval_reminder_attempt_count >= 0);

CREATE TABLE socialraven.post_collection_version (
    id                  BIGSERIAL      PRIMARY KEY,
    post_collection_id  BIGINT         NOT NULL REFERENCES socialraven.post_collection(id) ON DELETE CASCADE,
    workspace_id        VARCHAR(255)   NOT NULL REFERENCES socialraven.workspace(id) ON DELETE CASCADE,
    version_number      INTEGER        NOT NULL,
    version_event       VARCHAR(50)    NOT NULL,
    actor_type          VARCHAR(50)    NOT NULL DEFAULT 'WORKSPACE_USER',
    actor_user_id       VARCHAR(255),
    actor_display_name  VARCHAR(255),
    actor_email         VARCHAR(320),
    review_status       VARCHAR(50)    NOT NULL,
    is_draft            BOOLEAN        NOT NULL,
    scheduled_time      TIMESTAMPTZ(6),
    description         TEXT           NOT NULL,
    platform_configs    JSONB,
    target_accounts     JSONB          NOT NULL DEFAULT '[]'::jsonb,
    media_files         JSONB          NOT NULL DEFAULT '[]'::jsonb,
    created_at          TIMESTAMPTZ(6) NOT NULL DEFAULT NOW(),
    CONSTRAINT post_collection_version_unique
        UNIQUE (post_collection_id, version_number),
    CONSTRAINT post_collection_version_event_check
        CHECK (version_event IN (
            'CREATED',
            'UPDATED',
            'SUBMITTED',
            'RESUBMITTED',
            'STEP_APPROVED',
            'APPROVED',
            'CHANGES_REQUESTED',
            'REAPPROVAL_REQUIRED',
            'SCHEDULED_DIRECT',
            'RECOVERY_CREATED'
        )),
    CONSTRAINT post_collection_version_actor_type_check
        CHECK (actor_type IN ('WORKSPACE_USER', 'CLIENT_REVIEWER', 'SYSTEM')),
    CONSTRAINT post_collection_version_review_status_check
        CHECK (review_status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED'))
);

CREATE INDEX idx_post_collection_version_lookup
    ON socialraven.post_collection_version(post_collection_id, version_number DESC);

ALTER TABLE socialraven.post_collection
    ADD CONSTRAINT post_collection_last_approved_version_fk
        FOREIGN KEY (last_approved_version_id)
        REFERENCES socialraven.post_collection_version(id)
        ON DELETE SET NULL;

ALTER TABLE socialraven.post_collection_review_history
    DROP CONSTRAINT post_collection_review_history_action_check;

ALTER TABLE socialraven.post_collection_review_history
    ADD CONSTRAINT post_collection_review_history_action_check
        CHECK (action IN (
            'SUBMITTED',
            'RESUBMITTED',
            'STEP_APPROVED',
            'APPROVED',
            'CHANGES_REQUESTED',
            'REAPPROVAL_REQUIRED',
            'REMINDER_SENT',
            'ESCALATED'
        ));

ALTER TABLE socialraven.post_collection_review_history
    DROP CONSTRAINT post_collection_review_history_actor_type_check;

ALTER TABLE socialraven.post_collection_review_history
    ADD CONSTRAINT post_collection_review_history_actor_type_check
        CHECK (actor_type IN ('WORKSPACE_USER', 'CLIENT_REVIEWER', 'SYSTEM'));
