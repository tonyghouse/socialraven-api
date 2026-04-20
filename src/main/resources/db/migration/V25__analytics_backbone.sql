DROP TABLE IF EXISTS socialraven.analytics_jobs CASCADE;
DROP TABLE IF EXISTS socialraven.post_analytics_snapshots CASCADE;
DROP TABLE IF EXISTS socialraven.account_analytics_snapshots CASCADE;
DROP TABLE IF EXISTS socialraven.workspace_post_analytics CASCADE;
DROP TABLE IF EXISTS socialraven.analytics_provider_coverage CASCADE;

CREATE TABLE socialraven.analytics_jobs (
    id                             BIGSERIAL    PRIMARY KEY,
    workspace_id                   VARCHAR(255) NOT NULL REFERENCES socialraven.workspace(id),
    provider                       VARCHAR(20)  NOT NULL,
    provider_user_id               VARCHAR(255),
    post_id                        BIGINT       REFERENCES socialraven.post(id) ON DELETE CASCADE,
    provider_post_id               VARCHAR(255),
    snapshot_type                  VARCHAR(10),
    job_type                       VARCHAR(40)  NOT NULL,
    trigger_type                   VARCHAR(40)  NOT NULL,
    dedupe_key                     VARCHAR(255) NOT NULL,
    due_at                         TIMESTAMPTZ  NOT NULL,
    status                         VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    attempts                       INT          NOT NULL DEFAULT 0,
    last_attempted_at              TIMESTAMPTZ,
    started_at                     TIMESTAMPTZ,
    finished_at                    TIMESTAMPTZ,
    error_summary                  VARCHAR(2000),
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT analytics_jobs_snapshot_type_check
        CHECK (snapshot_type IS NULL OR snapshot_type IN ('T24H','T7D','T30D','T90D')),
    CONSTRAINT analytics_jobs_status_check
        CHECK (status IN ('PENDING','IN_PROGRESS','DONE','FAILED','SKIPPED')),
    CONSTRAINT analytics_jobs_type_check
        CHECK (job_type IN ('POST_CURRENT','POST_MILESTONE','ACCOUNT_DAILY','PROVIDER_RECONCILE')),
    CONSTRAINT analytics_jobs_trigger_check
        CHECK (trigger_type IN ('PUBLISH','WEBHOOK','MANUAL','SCHEDULED_DAILY','SCHEDULED_RECONCILE','RETRY')),
    CONSTRAINT analytics_jobs_dedupe_key_unique UNIQUE (dedupe_key)
);

CREATE INDEX idx_analytics_jobs_workspace_status_due
    ON socialraven.analytics_jobs(workspace_id, status, due_at);
CREATE INDEX idx_analytics_jobs_provider_user
    ON socialraven.analytics_jobs(workspace_id, provider, provider_user_id);
CREATE INDEX idx_analytics_jobs_post_id
    ON socialraven.analytics_jobs(post_id);

CREATE TABLE socialraven.post_analytics_snapshots (
    id                             BIGSERIAL    PRIMARY KEY,
    workspace_id                   VARCHAR(255) NOT NULL REFERENCES socialraven.workspace(id),
    post_id                        BIGINT       NOT NULL REFERENCES socialraven.post(id) ON DELETE CASCADE,
    post_collection_id             BIGINT       NOT NULL REFERENCES socialraven.post_collection(id) ON DELETE CASCADE,
    provider                       VARCHAR(20)  NOT NULL,
    provider_user_id               VARCHAR(255) NOT NULL,
    provider_post_id               VARCHAR(255) NOT NULL,
    snapshot_type                  VARCHAR(10)  NOT NULL,
    post_type                      VARCHAR(20)  NOT NULL,
    published_at                   TIMESTAMPTZ,
    impressions                    BIGINT,
    reach                          BIGINT,
    likes                          BIGINT,
    comments                       BIGINT,
    shares                         BIGINT,
    saves                          BIGINT,
    clicks                         BIGINT,
    video_views                    BIGINT,
    engagements                    BIGINT,
    engagement_rate                DOUBLE PRECISION,
    fetched_at                     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT post_analytics_snapshots_snapshot_type_check
        CHECK (snapshot_type IN ('T24H','T7D','T30D','T90D')),
    CONSTRAINT post_analytics_snapshots_post_type_check
        CHECK (post_type IN ('IMAGE','VIDEO','TEXT')),
    CONSTRAINT post_analytics_snapshots_unique UNIQUE (workspace_id, post_id, snapshot_type)
);

CREATE INDEX idx_post_analytics_snapshots_workspace_provider
    ON socialraven.post_analytics_snapshots(workspace_id, provider, fetched_at DESC);
CREATE INDEX idx_post_analytics_snapshots_provider_user
    ON socialraven.post_analytics_snapshots(workspace_id, provider_user_id, fetched_at DESC);

CREATE TABLE socialraven.account_analytics_snapshots (
    id                             BIGSERIAL    PRIMARY KEY,
    workspace_id                   VARCHAR(255) NOT NULL REFERENCES socialraven.workspace(id),
    provider_user_id               VARCHAR(255) NOT NULL,
    provider                       VARCHAR(20)  NOT NULL,
    snapshot_date                  DATE         NOT NULL,
    followers                      BIGINT,
    following                      BIGINT,
    impressions_day                BIGINT,
    reach_day                      BIGINT,
    fetched_at                     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT account_analytics_snapshots_unique
        UNIQUE (workspace_id, provider_user_id, provider, snapshot_date)
);

CREATE INDEX idx_account_analytics_snapshots_workspace_provider_user
    ON socialraven.account_analytics_snapshots(workspace_id, provider_user_id, provider, snapshot_date DESC);

CREATE TABLE socialraven.workspace_post_analytics (
    id                             BIGSERIAL    PRIMARY KEY,
    workspace_id                   VARCHAR(255) NOT NULL REFERENCES socialraven.workspace(id),
    post_id                        BIGINT       NOT NULL REFERENCES socialraven.post(id) ON DELETE CASCADE,
    post_collection_id             BIGINT       NOT NULL REFERENCES socialraven.post_collection(id) ON DELETE CASCADE,
    provider                       VARCHAR(20)  NOT NULL,
    provider_user_id               VARCHAR(255) NOT NULL,
    provider_post_id               VARCHAR(255) NOT NULL,
    post_type                      VARCHAR(20)  NOT NULL,
    published_at                   TIMESTAMPTZ,
    impressions                    BIGINT,
    reach                          BIGINT,
    likes                          BIGINT,
    comments                       BIGINT,
    shares                         BIGINT,
    saves                          BIGINT,
    clicks                         BIGINT,
    video_views                    BIGINT,
    engagements                    BIGINT,
    engagement_rate                DOUBLE PRECISION,
    freshness_status               VARCHAR(20)  NOT NULL DEFAULT 'NO_DATA',
    last_collected_at              TIMESTAMPTZ,
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT workspace_post_analytics_post_type_check
        CHECK (post_type IN ('IMAGE','VIDEO','TEXT')),
    CONSTRAINT workspace_post_analytics_freshness_check
        CHECK (freshness_status IN ('FRESH','DELAYED','STALE','NO_DATA')),
    CONSTRAINT workspace_post_analytics_post_unique UNIQUE (post_id)
);

CREATE INDEX idx_workspace_post_analytics_workspace_published
    ON socialraven.workspace_post_analytics(workspace_id, published_at DESC);
CREATE INDEX idx_workspace_post_analytics_workspace_provider
    ON socialraven.workspace_post_analytics(workspace_id, provider);
CREATE INDEX idx_workspace_post_analytics_provider_user
    ON socialraven.workspace_post_analytics(workspace_id, provider_user_id);

CREATE TABLE socialraven.analytics_provider_coverage (
    id                             BIGSERIAL    PRIMARY KEY,
    workspace_id                   VARCHAR(255) NOT NULL REFERENCES socialraven.workspace(id),
    provider                       VARCHAR(20)  NOT NULL,
    post_analytics_state           VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    account_analytics_state        VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    freshness_status               VARCHAR(20)  NOT NULL DEFAULT 'NO_DATA',
    last_post_analytics_at         TIMESTAMPTZ,
    last_account_analytics_at      TIMESTAMPTZ,
    last_successful_job_at         TIMESTAMPTZ,
    last_attempted_job_at          TIMESTAMPTZ,
    last_manual_refresh_requested_at TIMESTAMPTZ,
    last_error_summary             VARCHAR(2000),
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT analytics_provider_coverage_workspace_provider_unique
        UNIQUE (workspace_id, provider),
    CONSTRAINT analytics_provider_coverage_post_state_check
        CHECK (post_analytics_state IN ('PLANNED','LIVE','PARTIAL','UNSUPPORTED')),
    CONSTRAINT analytics_provider_coverage_account_state_check
        CHECK (account_analytics_state IN ('PLANNED','LIVE','PARTIAL','UNSUPPORTED')),
    CONSTRAINT analytics_provider_coverage_freshness_check
        CHECK (freshness_status IN ('FRESH','DELAYED','STALE','NO_DATA'))
);

CREATE INDEX idx_analytics_provider_coverage_workspace
    ON socialraven.analytics_provider_coverage(workspace_id, provider);
