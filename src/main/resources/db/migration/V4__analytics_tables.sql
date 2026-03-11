-- V4: Add provider_post_id to post table and create analytics tables

ALTER TABLE socialraven.post
    ADD COLUMN IF NOT EXISTS provider_post_id VARCHAR(255);

CREATE TABLE socialraven.analytics_jobs (
    id                 BIGSERIAL PRIMARY KEY,
    post_id            BIGINT       NOT NULL REFERENCES socialraven.post(id) ON DELETE CASCADE,
    provider           VARCHAR(20)  NOT NULL,
    provider_post_id   VARCHAR(255) NOT NULL,
    snapshot_type      VARCHAR(10)  NOT NULL,
    due_at             TIMESTAMPTZ  NOT NULL,
    status             VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    attempts           INT          NOT NULL DEFAULT 0,
    last_attempted_at  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analytics_jobs_status_due  ON socialraven.analytics_jobs (status, due_at);
CREATE INDEX idx_analytics_jobs_post_id     ON socialraven.analytics_jobs (post_id);

ALTER TABLE socialraven.analytics_jobs
    ADD CONSTRAINT analytics_jobs_snapshot_type_check
        CHECK (snapshot_type IN ('T24H','T7D','T30D','T90D'));

ALTER TABLE socialraven.analytics_jobs
    ADD CONSTRAINT analytics_jobs_status_check
        CHECK (status IN ('PENDING','IN_PROGRESS','DONE','FAILED','SKIPPED'));

CREATE TABLE socialraven.post_analytics_snapshots (
    id               BIGSERIAL    PRIMARY KEY,
    post_id          BIGINT       NOT NULL REFERENCES socialraven.post(id) ON DELETE CASCADE,
    provider         VARCHAR(20)  NOT NULL,
    provider_post_id VARCHAR(255) NOT NULL,
    snapshot_type    VARCHAR(10)  NOT NULL,
    impressions      BIGINT,
    reach            BIGINT,
    likes            BIGINT,
    comments         BIGINT,
    shares           BIGINT,
    saves            BIGINT,
    clicks           BIGINT,
    video_views      BIGINT,
    fetched_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_post_snapshots_post_id          ON socialraven.post_analytics_snapshots (post_id);
CREATE INDEX idx_post_snapshots_provider_fetched ON socialraven.post_analytics_snapshots (provider, fetched_at DESC);

CREATE TABLE socialraven.account_analytics_snapshots (
    id               BIGSERIAL    PRIMARY KEY,
    provider_user_id VARCHAR(255) NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    snapshot_date    DATE         NOT NULL,
    followers        BIGINT,
    following        BIGINT,
    impressions_day  BIGINT,
    reach_day        BIGINT,
    fetched_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (provider_user_id, provider, snapshot_date)
);

CREATE INDEX idx_account_snapshots_lookup ON socialraven.account_analytics_snapshots (provider_user_id, provider, snapshot_date DESC);
