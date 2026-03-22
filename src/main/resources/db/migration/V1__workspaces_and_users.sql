CREATE SCHEMA IF NOT EXISTS socialraven;

-- ─────────────────────────────────────────────
-- workspace
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.workspace (
    id            VARCHAR(255) PRIMARY KEY,  -- UUID for named; "personal_<clerkUserId>" for personal
    name          VARCHAR(255) NOT NULL,
    company_name  VARCHAR(255),
    owner_user_id VARCHAR(255) NOT NULL,     -- clerkUserId — the billable party
    logo_s3_key   VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ                          -- soft-delete; hard-deleted after 30 days
);

-- Partial index — only indexes soft-deleted rows so the scheduler query is fast
CREATE INDEX idx_workspace_deleted_at
    ON socialraven.workspace(deleted_at)
    WHERE deleted_at IS NOT NULL;

-- ─────────────────────────────────────────────
-- workspace_member
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.workspace_member (
    id           BIGSERIAL    PRIMARY KEY,
    workspace_id VARCHAR(255) NOT NULL REFERENCES socialraven.workspace(id),
    user_id      VARCHAR(255) NOT NULL,  -- clerkUserId
    role         VARCHAR(50)  NOT NULL,  -- OWNER, ADMIN, MEMBER, VIEWER
    joined_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, user_id)
);

CREATE INDEX idx_wm_user      ON socialraven.workspace_member(user_id);
CREATE INDEX idx_wm_workspace ON socialraven.workspace_member(workspace_id);

-- ─────────────────────────────────────────────
-- workspace_invitation
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.workspace_invitation (
    token         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  VARCHAR(255) NOT NULL REFERENCES socialraven.workspace(id),
    invited_email VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL,
    invited_by    VARCHAR(255) NOT NULL,  -- clerkUserId of inviter
    expires_at    TIMESTAMPTZ  NOT NULL,
    accepted_at   TIMESTAMPTZ,           -- NULL until accepted
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
