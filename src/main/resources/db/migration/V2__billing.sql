-- ─────────────────────────────────────────────
-- plan_config  (source of truth for default limits per plan/user-type)
-- posts_per_month / accounts_limit / max_workspaces: -1 = unlimited, NULL = unlimited
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.plan_config (
    plan_type        VARCHAR(50)   PRIMARY KEY,
    user_type        VARCHAR(20)   NOT NULL,      -- INFLUENCER, AGENCY
    posts_per_month  INTEGER,                     -- NULL = unlimited
    accounts_limit   INTEGER,                     -- NULL = unlimited
    max_workspaces   INTEGER       NOT NULL DEFAULT 1,  -- -1 = unlimited
    price_usd        DECIMAL(10,2) NOT NULL DEFAULT 0,
    trial_days       INTEGER
);

INSERT INTO socialraven.plan_config
    (plan_type,            user_type,     posts_per_month, accounts_limit, max_workspaces, price_usd, trial_days)
VALUES
    ('INFLUENCER_TRIAL',   'INFLUENCER',  50,              5,              1,              0.00,      14),
    ('INFLUENCER_BASE',    'INFLUENCER',  100,             5,              1,              12.00,     NULL),
    ('INFLUENCER_PRO',     'INFLUENCER',  500,             15,             1,              29.00,     NULL),
    ('AGENCY_TRIAL',   'AGENCY',  300,             10,             3,              0.00,      14),
    ('AGENCY_BASE',    'AGENCY',  300,             10,             3,              79.00,     NULL),
    ('AGENCY_PRO',     'AGENCY',  1000,            30,             10,             199.00,    NULL),
    ('AGENCY_CUSTOM',  'AGENCY',  -1,              -1,             -1,             0.00,      NULL);

-- ─────────────────────────────────────────────
-- user_plan  (per-workspace plan assignment with optional custom limit overrides)
-- ─────────────────────────────────────────────
CREATE TABLE socialraven.user_plan (
    id                     BIGSERIAL    PRIMARY KEY,
    workspace_id           VARCHAR(255) NOT NULL REFERENCES socialraven.workspace(id),
    user_id                VARCHAR(255) NOT NULL UNIQUE,
    plan_type              VARCHAR(50)  NOT NULL DEFAULT 'INFLUENCER_TRIAL',
    status                 VARCHAR(50)  NOT NULL DEFAULT 'TRIALING',
    renewal_date           TIMESTAMPTZ  NOT NULL,
    trial_ends_at          TIMESTAMPTZ,
    cancel_at_period_end   BOOLEAN      NOT NULL DEFAULT FALSE,
    stripe_subscription_id VARCHAR(1000),
    custom_posts_limit     INTEGER,     -- NULL = use plan_config default
    custom_accounts_limit  INTEGER,     -- NULL = use plan_config default
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_plan_type   FOREIGN KEY (plan_type) REFERENCES socialraven.plan_config(plan_type),
    CONSTRAINT fk_plan_workspace   FOREIGN KEY (workspace_id) REFERENCES socialraven.workspace(id),
    CONSTRAINT user_plan_status_ck CHECK (status IN ('TRIALING','ACTIVE','PAST_DUE','CANCELLED'))
);

CREATE INDEX idx_plan_workspace ON socialraven.user_plan(workspace_id);
