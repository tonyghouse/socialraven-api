ALTER TABLE socialraven.post_collection
    ADD COLUMN failure_state VARCHAR(50) NOT NULL DEFAULT 'NONE',
    ADD COLUMN failure_detected_at TIMESTAMPTZ(6),
    ADD COLUMN failure_reason_summary VARCHAR(2000),
    ADD COLUMN notification_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_notification_sent_at TIMESTAMPTZ(6),
    ADD COLUMN next_notification_at TIMESTAMPTZ(6),
    ADD COLUMN notification_stopped_at TIMESTAMPTZ(6),
    ADD COLUMN recovery_collection_id BIGINT REFERENCES socialraven.post_collection(id),
    ADD COLUMN recovery_source_collection_id BIGINT REFERENCES socialraven.post_collection(id),
    ADD COLUMN handled_at TIMESTAMPTZ(6),
    ADD COLUMN handled_by VARCHAR(255),
    ADD COLUMN admin_escalated_at TIMESTAMPTZ(6);

ALTER TABLE socialraven.post_collection
    ADD CONSTRAINT post_collection_failure_state_check
        CHECK (failure_state IN ('NONE', 'RECOVERY_REQUIRED', 'RECOVERED', 'ESCALATED_TO_ADMIN'));

CREATE INDEX idx_post_collection_failure_state
    ON socialraven.post_collection(failure_state, next_notification_at);

CREATE INDEX idx_post_collection_recovery_collection
    ON socialraven.post_collection(recovery_collection_id);

CREATE INDEX idx_post_collection_recovery_source
    ON socialraven.post_collection(recovery_source_collection_id);
