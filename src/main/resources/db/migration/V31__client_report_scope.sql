ALTER TABLE socialraven.workspace_client_report_link
    ADD COLUMN report_scope VARCHAR(50) NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN campaign_id BIGINT,
    ADD CONSTRAINT workspace_client_report_link_scope_check
        CHECK (report_scope IN ('WORKSPACE', 'CAMPAIGN')),
    ADD CONSTRAINT workspace_client_report_link_scope_campaign_check
        CHECK (
            (report_scope = 'WORKSPACE' AND campaign_id IS NULL)
            OR (report_scope = 'CAMPAIGN' AND campaign_id IS NOT NULL)
        );

ALTER TABLE socialraven.workspace_client_report_schedule
    ADD COLUMN report_scope VARCHAR(50) NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN campaign_id BIGINT,
    ADD CONSTRAINT workspace_client_report_schedule_scope_check
        CHECK (report_scope IN ('WORKSPACE', 'CAMPAIGN')),
    ADD CONSTRAINT workspace_client_report_schedule_scope_campaign_check
        CHECK (
            (report_scope = 'WORKSPACE' AND campaign_id IS NULL)
            OR (report_scope = 'CAMPAIGN' AND campaign_id IS NOT NULL)
        );

CREATE INDEX idx_workspace_client_report_link_scope_lookup
    ON socialraven.workspace_client_report_link(workspace_id, report_scope, campaign_id, created_at DESC);

CREATE INDEX idx_workspace_client_report_schedule_scope_lookup
    ON socialraven.workspace_client_report_schedule(workspace_id, report_scope, campaign_id, next_send_at);
