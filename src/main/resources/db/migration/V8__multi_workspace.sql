-- V8: Multi-workspace support
-- Adds workspace tables, user_profile, workspace-scopes all data tables,
-- new plan tiers, backfills all existing rows into personal workspaces,
-- then enforces NOT NULL on all workspace_id columns.

-- ─────────────────────────────────────────────
-- 1. workspace
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.workspace (
    id            VARCHAR(255) PRIMARY KEY,  -- UUID for named; "personal_<clerkUserId>" for personal
    name          VARCHAR(255) NOT NULL,
    company_name  VARCHAR(255),
    owner_user_id VARCHAR(255) NOT NULL,     -- clerkUserId — the billable party
    logo_s3_key   VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────
-- 2. workspace_member
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
-- 3. workspace_invitation
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
-- 4. workspace_settings
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.workspace_settings (
    workspace_id  VARCHAR(255) PRIMARY KEY REFERENCES socialraven.workspace(id),
    default_tz    VARCHAR(100) NOT NULL DEFAULT 'UTC',
    brand_color   VARCHAR(10),
    custom_domain VARCHAR(255),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────
-- 5. user_profile
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.user_profile (
    user_id    VARCHAR(255) PRIMARY KEY,  -- clerkUserId
    user_type  VARCHAR(20)  NOT NULL,     -- INFLUENCER, AGENCY
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────
-- 6. Rename post_collection.user_id → created_by
--    (attribution only — workspace_id is the data boundary)
-- ─────────────────────────────────────────────
ALTER TABLE socialraven.post_collection RENAME COLUMN user_id TO created_by;

-- ─────────────────────────────────────────────
-- 7. Add workspace_id to scoped tables (NULLable until backfill in step 9)
-- ─────────────────────────────────────────────
ALTER TABLE socialraven.post_collection             ADD COLUMN workspace_id VARCHAR(255);
ALTER TABLE socialraven.oauth_info                  ADD COLUMN workspace_id VARCHAR(255);
ALTER TABLE socialraven.user_plan                   ADD COLUMN workspace_id VARCHAR(255);
ALTER TABLE socialraven.analytics_jobs              ADD COLUMN workspace_id VARCHAR(255);
ALTER TABLE socialraven.post_analytics_snapshots    ADD COLUMN workspace_id VARCHAR(255);
ALTER TABLE socialraven.account_analytics_snapshots ADD COLUMN workspace_id VARCHAR(255);

CREATE INDEX idx_pc_workspace       ON socialraven.post_collection(workspace_id);
CREATE INDEX idx_oauth_workspace    ON socialraven.oauth_info(workspace_id);
CREATE INDEX idx_plan_workspace     ON socialraven.user_plan(workspace_id);
CREATE INDEX idx_aj_workspace       ON socialraven.analytics_jobs(workspace_id);
CREATE INDEX idx_pas_workspace      ON socialraven.post_analytics_snapshots(workspace_id);
CREATE INDEX idx_aas_workspace      ON socialraven.account_analytics_snapshots(workspace_id);

-- ─────────────────────────────────────────────
-- 8. plan_config — add workspace/user-type columns and new plan tiers
-- ─────────────────────────────────────────────
ALTER TABLE socialraven.plan_config
    ADD COLUMN max_workspaces INT         NOT NULL DEFAULT 1;

ALTER TABLE socialraven.plan_config
    ADD COLUMN user_type      VARCHAR(20) NOT NULL DEFAULT 'INFLUENCER';

-- Tag existing legacy plans
UPDATE socialraven.plan_config SET user_type = 'INFLUENCER', max_workspaces = 1
WHERE plan_type IN ('TRIAL', 'BASE', 'PRO');

UPDATE socialraven.plan_config SET user_type = 'AGENCY', max_workspaces = -1
WHERE plan_type = 'ENTERPRISE';

-- New plan tiers  (-1 = unlimited)
INSERT INTO socialraven.plan_config
    (plan_type, posts_per_month, accounts_limit, price_usd, trial_days, max_workspaces, user_type)
VALUES
    ('INFLUENCER_BASE',  100,  5,    0.00, 14, 1,  'INFLUENCER'),
    ('INFLUENCER_PRO',   500,  15,   0.00, 14, 1,  'INFLUENCER'),
    ('AGENCY_BASE',      300,  10,   0.00, 14, 3,  'AGENCY'),
    ('AGENCY_PRO',       1000, 30,   0.00, 14, 10, 'AGENCY'),
    ('AGENCY_CUSTOM',    -1,   -1,   0.00, 14, -1, 'AGENCY');

-- ─────────────────────────────────────────────
-- 9. Backfill — existing users become INFLUENCER with a "main" personal workspace
-- ─────────────────────────────────────────────

-- 9a. user_profile — union of all known clerkUserIds
INSERT INTO socialraven.user_profile (user_id, user_type)
SELECT DISTINCT created_by, 'INFLUENCER'
FROM socialraven.post_collection
UNION
SELECT DISTINCT user_id, 'INFLUENCER'
FROM socialraven.oauth_info
ON CONFLICT (user_id) DO NOTHING;

-- 9b. personal workspace per user
INSERT INTO socialraven.workspace (id, name, owner_user_id)
SELECT DISTINCT 'personal_' || up.user_id, 'main', up.user_id
FROM socialraven.user_profile up
ON CONFLICT (id) DO NOTHING;

-- 9c. owner membership row
INSERT INTO socialraven.workspace_member (workspace_id, user_id, role)
SELECT DISTINCT 'personal_' || up.user_id, up.user_id, 'OWNER'
FROM socialraven.user_profile up
ON CONFLICT (workspace_id, user_id) DO NOTHING;

-- 9d. workspace_settings stub per workspace
INSERT INTO socialraven.workspace_settings (workspace_id)
SELECT id FROM socialraven.workspace
ON CONFLICT (workspace_id) DO NOTHING;

-- 9e. Scope post_collection
UPDATE socialraven.post_collection
SET workspace_id = 'personal_' || created_by;

-- 9f. Scope oauth_info
UPDATE socialraven.oauth_info
SET workspace_id = 'personal_' || user_id;

-- 9g. Scope user_plan
UPDATE socialraven.user_plan
SET workspace_id = 'personal_' || user_id;

-- 9h. Scope analytics_jobs (via post → post_collection)
UPDATE socialraven.analytics_jobs aj
SET workspace_id = pc.workspace_id
FROM socialraven.post p
JOIN socialraven.post_collection pc ON pc.id = p.post_collection_id
WHERE aj.post_id = p.id;

-- 9i. Scope post_analytics_snapshots (via post → post_collection)
UPDATE socialraven.post_analytics_snapshots pas
SET workspace_id = pc.workspace_id
FROM socialraven.post p
JOIN socialraven.post_collection pc ON pc.id = p.post_collection_id
WHERE pas.post_id = p.id;

-- 9j. Scope account_analytics_snapshots (via oauth_info provider match)
UPDATE socialraven.account_analytics_snapshots aas
SET workspace_id = oi.workspace_id
FROM socialraven.oauth_info oi
WHERE aas.provider_user_id = oi.provider_user_id
  AND aas.provider          = oi.provider;

-- ─────────────────────────────────────────────
-- 10. FK constraints (added after backfill so no rows are orphaned)
-- ─────────────────────────────────────────────
ALTER TABLE socialraven.post_collection
    ADD CONSTRAINT fk_pc_workspace
        FOREIGN KEY (workspace_id) REFERENCES socialraven.workspace(id);

ALTER TABLE socialraven.oauth_info
    ADD CONSTRAINT fk_oauth_workspace
        FOREIGN KEY (workspace_id) REFERENCES socialraven.workspace(id);

ALTER TABLE socialraven.user_plan
    ADD CONSTRAINT fk_plan_workspace
        FOREIGN KEY (workspace_id) REFERENCES socialraven.workspace(id);

ALTER TABLE socialraven.analytics_jobs
    ADD CONSTRAINT fk_aj_workspace
        FOREIGN KEY (workspace_id) REFERENCES socialraven.workspace(id);

ALTER TABLE socialraven.post_analytics_snapshots
    ADD CONSTRAINT fk_pas_workspace
        FOREIGN KEY (workspace_id) REFERENCES socialraven.workspace(id);

ALTER TABLE socialraven.account_analytics_snapshots
    ADD CONSTRAINT fk_aas_workspace
        FOREIGN KEY (workspace_id) REFERENCES socialraven.workspace(id);

-- ─────────────────────────────────────────────
-- 11. Enforce NOT NULL on workspace_id (backfill is complete)
-- ─────────────────────────────────────────────
ALTER TABLE socialraven.post_collection             ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE socialraven.oauth_info                  ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE socialraven.user_plan                   ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE socialraven.analytics_jobs              ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE socialraven.post_analytics_snapshots    ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE socialraven.account_analytics_snapshots ALTER COLUMN workspace_id SET NOT NULL;
