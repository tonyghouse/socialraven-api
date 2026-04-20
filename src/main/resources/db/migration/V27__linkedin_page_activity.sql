ALTER TABLE socialraven.account_analytics_snapshots
    ADD COLUMN IF NOT EXISTS page_views_day BIGINT,
    ADD COLUMN IF NOT EXISTS unique_page_views_day BIGINT,
    ADD COLUMN IF NOT EXISTS clicks_day BIGINT;
