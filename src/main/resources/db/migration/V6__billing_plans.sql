-- Plan reference table (source of truth for default limits per plan type)
CREATE TABLE socialraven.plan_config (
    plan_type        VARCHAR(50)   PRIMARY KEY,
    posts_per_month  INTEGER,                        -- NULL = unlimited
    accounts_limit   INTEGER,                        -- NULL = unlimited
    price_usd        DECIMAL(10,2) NOT NULL DEFAULT 0,
    trial_days       INTEGER
);

INSERT INTO socialraven.plan_config (plan_type, posts_per_month, accounts_limit, price_usd, trial_days) VALUES
    ('TRIAL',      50,   5,    0.00,    14),
    ('BASE',       500,  30,   15.00,   NULL),
    ('PRO',        2000, 100,  25.00,   NULL),
    ('ENTERPRISE', NULL, NULL, 1000.00, NULL);

-- Per-user plan assignment with optional custom limit overrides
CREATE TABLE socialraven.user_plan (
    id                     BIGSERIAL    PRIMARY KEY,
    user_id                VARCHAR(255) NOT NULL UNIQUE,
    plan_type              VARCHAR(50)  NOT NULL DEFAULT 'TRIAL',
    status                 VARCHAR(50)  NOT NULL DEFAULT 'TRIALING',
    renewal_date           TIMESTAMPTZ  NOT NULL,
    trial_ends_at          TIMESTAMPTZ,
    cancel_at_period_end   BOOLEAN      NOT NULL DEFAULT FALSE,
    stripe_subscription_id VARCHAR(1000),
    custom_posts_limit     INTEGER,   -- NULL = use plan_config default
    custom_accounts_limit  INTEGER,   -- NULL = use plan_config default
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_plan_type   FOREIGN KEY (plan_type) REFERENCES socialraven.plan_config(plan_type),
    CONSTRAINT user_plan_status_ck CHECK (status IN ('TRIALING','ACTIVE','PAST_DUE','CANCELLED'))
);
