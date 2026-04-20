ALTER TABLE socialraven.workspace_post_analytics
    ADD COLUMN IF NOT EXISTS watch_time_minutes BIGINT;

ALTER TABLE socialraven.post_analytics_snapshots
    ADD COLUMN IF NOT EXISTS watch_time_minutes BIGINT;

ALTER TABLE socialraven.account_analytics_snapshots
    ADD COLUMN IF NOT EXISTS video_views_day BIGINT,
    ADD COLUMN IF NOT EXISTS likes_day BIGINT,
    ADD COLUMN IF NOT EXISTS comments_day BIGINT,
    ADD COLUMN IF NOT EXISTS shares_day BIGINT,
    ADD COLUMN IF NOT EXISTS watch_time_minutes_day BIGINT,
    ADD COLUMN IF NOT EXISTS subscriber_delta_day BIGINT;
