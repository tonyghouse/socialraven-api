CREATE SCHEMA IF NOT EXISTS socialraven;

-- ─────────────────────────────────────────────
-- company
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.company (
    id            VARCHAR(255) PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    owner_user_id VARCHAR(255) NOT NULL,     -- clerkUserId of the company owner
    logo_s3_key   VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────
-- company_user
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.company_user (
    id         BIGSERIAL    PRIMARY KEY,
    company_id VARCHAR(255) NOT NULL REFERENCES socialraven.company(id),
    user_id    VARCHAR(255) NOT NULL,  -- clerkUserId
    role       VARCHAR(50)  NOT NULL,  -- OWNER, ADMIN, EDITOR, READ_ONLY
    joined_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (company_id, user_id)
);

CREATE INDEX idx_cu_user    ON socialraven.company_user(user_id);
CREATE INDEX idx_cu_company ON socialraven.company_user(company_id);

-- ─────────────────────────────────────────────
-- workspace
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.workspace (
    id          VARCHAR(255) PRIMARY KEY,  -- UUID for named; "personal_<clerkUserId>" for personal
    company_id  VARCHAR(255) NOT NULL REFERENCES socialraven.company(id),
    name        VARCHAR(255) NOT NULL,
    logo_s3_key VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ                          -- soft-delete; hard-deleted after 30 days
);

CREATE INDEX idx_workspace_deleted_at
    ON socialraven.workspace(deleted_at)
    WHERE deleted_at IS NOT NULL;

-- ─────────────────────────────────────────────
-- workspace_user
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.workspace_user (
    id           BIGSERIAL    PRIMARY KEY,
    workspace_id VARCHAR(255) NOT NULL REFERENCES socialraven.workspace(id),
    user_id      VARCHAR(255) NOT NULL,  -- clerkUserId
    role         VARCHAR(50)  NOT NULL,  -- OWNER, ADMIN, EDITOR, READ_ONLY
    joined_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, user_id)
);

CREATE INDEX idx_wu_user      ON socialraven.workspace_user(user_id);
CREATE INDEX idx_wu_workspace ON socialraven.workspace_user(workspace_id);

-- ─────────────────────────────────────────────
-- workspace_invitation
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.workspace_invitation (
    token         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  VARCHAR(255) NOT NULL REFERENCES socialraven.workspace(id),
    invited_email VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL,  -- ADMIN, EDITOR, READ_ONLY
    invited_by    VARCHAR(255) NOT NULL,  -- clerkUserId of inviter
    expires_at    TIMESTAMPTZ  NOT NULL,
    accepted_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────
-- workspace_settings
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.workspace_settings (
    workspace_id  VARCHAR(255) PRIMARY KEY REFERENCES socialraven.workspace(id),
    default_tz    VARCHAR(100) NOT NULL DEFAULT 'UTC',
    brand_color   VARCHAR(10),
    custom_domain VARCHAR(255),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────
-- user_profile
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.user_profile (
    user_id    VARCHAR(255) PRIMARY KEY,  -- clerkUserId
    user_type  VARCHAR(20)  NOT NULL,     -- INFLUENCER, AGENCY
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
