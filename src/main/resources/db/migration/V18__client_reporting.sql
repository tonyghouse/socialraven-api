CREATE TABLE socialraven.workspace_client_report_link (
    id                  VARCHAR(36)     PRIMARY KEY,
    workspace_id        VARCHAR(255)    NOT NULL,
    created_by_user_id  VARCHAR(255)    NOT NULL,
    schedule_id         BIGINT,
    report_title        VARCHAR(255)    NOT NULL,
    client_label        VARCHAR(255),
    agency_label        VARCHAR(255),
    template_type       VARCHAR(50)     NOT NULL,
    report_days         INTEGER         NOT NULL,
    commentary          VARCHAR(4000),
    recipient_name      VARCHAR(255),
    recipient_email     VARCHAR(320),
    expires_at          TIMESTAMPTZ(6)  NOT NULL,
    revoked_at          TIMESTAMPTZ(6),
    revoked_by_user_id  VARCHAR(255),
    last_accessed_at    TIMESTAMPTZ(6),
    created_at          TIMESTAMPTZ(6)  NOT NULL,
    CONSTRAINT workspace_client_report_link_template_check
        CHECK (template_type IN ('EXECUTIVE_SUMMARY', 'ENGAGEMENT_SPOTLIGHT', 'GROWTH_SNAPSHOT')),
    CONSTRAINT workspace_client_report_link_report_days_check
        CHECK (report_days >= 7 AND report_days <= 365)
);

CREATE INDEX idx_workspace_client_report_link_lookup
    ON socialraven.workspace_client_report_link(workspace_id, revoked_at, expires_at DESC);

CREATE INDEX idx_workspace_client_report_link_schedule_lookup
    ON socialraven.workspace_client_report_link(schedule_id, created_at DESC);

CREATE TABLE socialraven.workspace_client_report_schedule (
    id                  BIGSERIAL       PRIMARY KEY,
    workspace_id        VARCHAR(255)    NOT NULL,
    created_by_user_id  VARCHAR(255)    NOT NULL,
    report_title        VARCHAR(255)    NOT NULL,
    recipient_name      VARCHAR(255),
    recipient_email     VARCHAR(320)    NOT NULL,
    client_label        VARCHAR(255),
    agency_label        VARCHAR(255),
    template_type       VARCHAR(50)     NOT NULL,
    report_days         INTEGER         NOT NULL,
    commentary          VARCHAR(4000),
    cadence             VARCHAR(50)     NOT NULL,
    day_of_week         INTEGER,
    day_of_month        INTEGER,
    hour_of_day_utc     INTEGER         NOT NULL,
    share_expiry_hours  INTEGER         NOT NULL,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    last_sent_at        TIMESTAMPTZ(6),
    next_send_at        TIMESTAMPTZ(6)  NOT NULL,
    deactivated_at      TIMESTAMPTZ(6),
    created_at          TIMESTAMPTZ(6)  NOT NULL,
    updated_at          TIMESTAMPTZ(6)  NOT NULL,
    CONSTRAINT workspace_client_report_schedule_template_check
        CHECK (template_type IN ('EXECUTIVE_SUMMARY', 'ENGAGEMENT_SPOTLIGHT', 'GROWTH_SNAPSHOT')),
    CONSTRAINT workspace_client_report_schedule_cadence_check
        CHECK (cadence IN ('WEEKLY', 'MONTHLY')),
    CONSTRAINT workspace_client_report_schedule_report_days_check
        CHECK (report_days >= 7 AND report_days <= 365),
    CONSTRAINT workspace_client_report_schedule_hour_check
        CHECK (hour_of_day_utc >= 0 AND hour_of_day_utc <= 23),
    CONSTRAINT workspace_client_report_schedule_share_expiry_check
        CHECK (share_expiry_hours >= 24 AND share_expiry_hours <= 744),
    CONSTRAINT workspace_client_report_schedule_weekly_check
        CHECK (
            (cadence = 'WEEKLY' AND day_of_week BETWEEN 1 AND 7 AND day_of_month IS NULL)
            OR (cadence = 'MONTHLY' AND day_of_month BETWEEN 1 AND 28 AND day_of_week IS NULL)
        )
);

CREATE INDEX idx_workspace_client_report_schedule_lookup
    ON socialraven.workspace_client_report_schedule(workspace_id, active, next_send_at);
