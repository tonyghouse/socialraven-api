ALTER TABLE socialraven.account_analytics_snapshots
    ADD COLUMN IF NOT EXISTS likes_total BIGINT,
    ADD COLUMN IF NOT EXISTS video_count BIGINT;
