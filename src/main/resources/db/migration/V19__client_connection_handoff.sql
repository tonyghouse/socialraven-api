CREATE TABLE socialraven.workspace_client_connection_session (
    id                  VARCHAR(36)     PRIMARY KEY,
    workspace_id        VARCHAR(255)    NOT NULL REFERENCES socialraven.workspace(id) ON DELETE CASCADE,
    created_by_user_id  VARCHAR(255)    NOT NULL,
    recipient_name      VARCHAR(255),
    recipient_email     VARCHAR(320)    NOT NULL,
    client_label        VARCHAR(255),
    agency_label        VARCHAR(255),
    message             VARCHAR(4000),
    allowed_platforms   JSONB           NOT NULL DEFAULT '["x","linkedin","youtube","instagram","facebook"]'::jsonb,
    expires_at          TIMESTAMPTZ(6)  NOT NULL,
    revoked_at          TIMESTAMPTZ(6),
    revoked_by_user_id  VARCHAR(255),
    last_accessed_at    TIMESTAMPTZ(6),
    created_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workspace_client_connection_session_lookup
    ON socialraven.workspace_client_connection_session(workspace_id, revoked_at, expires_at DESC);

CREATE TABLE socialraven.workspace_client_connection_audit (
    id                  BIGSERIAL       PRIMARY KEY,
    session_id          VARCHAR(36)     NOT NULL REFERENCES socialraven.workspace_client_connection_session(id) ON DELETE CASCADE,
    workspace_id        VARCHAR(255)    NOT NULL REFERENCES socialraven.workspace(id) ON DELETE CASCADE,
    provider            VARCHAR(50)     NOT NULL,
    provider_user_id    VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(50)     NOT NULL,
    actor_display_name  VARCHAR(255)    NOT NULL,
    actor_email         VARCHAR(320)    NOT NULL,
    created_at          TIMESTAMPTZ(6)  NOT NULL DEFAULT NOW(),
    CONSTRAINT workspace_client_connection_audit_provider_check
        CHECK (provider IN ('INSTAGRAM','X','LINKEDIN','FACEBOOK','YOUTUBE','TIKTOK','THREADS')),
    CONSTRAINT workspace_client_connection_audit_event_type_check
        CHECK (event_type IN ('CONNECTED', 'RECONNECTED'))
);

CREATE INDEX idx_workspace_client_connection_audit_lookup
    ON socialraven.workspace_client_connection_audit(session_id, created_at DESC);

ALTER TABLE socialraven.oauth_info
    ADD COLUMN connection_owner_type VARCHAR(50) NOT NULL DEFAULT 'WORKSPACE_MEMBER',
    ADD COLUMN connection_owner_display_name VARCHAR(255),
    ADD COLUMN connection_owner_email VARCHAR(320),
    ADD COLUMN client_connection_session_id VARCHAR(36),
    ADD COLUMN connected_at TIMESTAMPTZ(6) NOT NULL DEFAULT NOW(),
    ADD COLUMN last_reauthorized_at TIMESTAMPTZ(6);

ALTER TABLE socialraven.oauth_info
    ADD CONSTRAINT oauth_info_connection_owner_type_check
        CHECK (connection_owner_type IN ('WORKSPACE_MEMBER', 'CLIENT_HANDOFF'));
