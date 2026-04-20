ALTER TABLE socialraven.workspace_post_analytics
    ADD COLUMN IF NOT EXISTS metric_availability JSONB;
