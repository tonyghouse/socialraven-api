-- ─────────────────────────────────────────────
-- oauth_info
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.oauth_info (
    id               BIGSERIAL     PRIMARY KEY,
    workspace_id     VARCHAR(255)  NOT NULL REFERENCES socialraven.workspace(id),
    user_id          VARCHAR(255)  NOT NULL,
    provider         VARCHAR(50)   NOT NULL,
    provider_user_id VARCHAR(255)  NOT NULL,
    access_token     VARCHAR(10000) NOT NULL,
    additional_info  JSONB         NOT NULL,
    expires_at       BIGINT        NOT NULL,
    expires_at_utc   TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT oauth_info_provider_check
        CHECK (provider IN ('INSTAGRAM','X','LINKEDIN','FACEBOOK','YOUTUBE','TIKTOK','THREADS'))
);

CREATE INDEX idx_oauth_workspace ON socialraven.oauth_info(workspace_id);

-- ─────────────────────────────────────────────
-- post_collection  (campaign / content bundle)
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.post_collection (
    id                      BIGSERIAL      PRIMARY KEY,
    workspace_id            VARCHAR(255)   NOT NULL REFERENCES socialraven.workspace(id),
    created_by              VARCHAR(255)   NOT NULL,  -- clerkUserId
    title                   VARCHAR(1000)  NOT NULL,
    description             VARCHAR(100000) NOT NULL,
    post_collection_status  VARCHAR(50)    NOT NULL,
    post_collection_type    VARCHAR(50)    NOT NULL,
    scheduled_time          TIMESTAMPTZ(6),
    platform_configs        JSONB,
    CONSTRAINT post_collection_status_check
        CHECK (post_collection_status IN ('DRAFT','SCHEDULED','SUCCESS','PARTIAL_SUCCESS','FAILED')),
    CONSTRAINT post_collection_type_check
        CHECK (post_collection_type IN ('IMAGE','VIDEO','TEXT'))
);

CREATE INDEX idx_pc_workspace ON socialraven.post_collection(workspace_id);

-- ─────────────────────────────────────────────
-- post
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.post (
    id                  BIGSERIAL     PRIMARY KEY,
    post_collection_id  BIGINT        NOT NULL REFERENCES socialraven.post_collection(id),
    provider            VARCHAR(50)   NOT NULL,
    provider_user_id    VARCHAR(1000) NOT NULL,
    provider_post_id    VARCHAR(255),
    post_status         VARCHAR(50)   NOT NULL,
    post_type           VARCHAR(50)   NOT NULL,
    scheduled_time      TIMESTAMPTZ(6),
    created_at          TIMESTAMPTZ(6),
    updated_at          TIMESTAMPTZ(6),
    CONSTRAINT post_post_status_check
        CHECK (post_status IN ('DRAFT','SCHEDULED','POSTED','FAILED')),
    CONSTRAINT post_post_type_check
        CHECK (post_type IN ('IMAGE','VIDEO','TEXT')),
    CONSTRAINT post_provider_check
        CHECK (provider IN ('INSTAGRAM','X','LINKEDIN','FACEBOOK','YOUTUBE','TIKTOK','THREADS'))
);

-- ─────────────────────────────────────────────
-- post_media
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.post_media (
    id                  BIGSERIAL    PRIMARY KEY,
    post_collection_id  BIGINT       NOT NULL REFERENCES socialraven.post_collection(id),
    file_key            VARCHAR(255) NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    mime_type           VARCHAR(255) NOT NULL,
    size                BIGINT       NOT NULL
);
