CREATE TABLE socialraven.workspace_approval_rule (
    id            BIGSERIAL    PRIMARY KEY,
    workspace_id  VARCHAR(255) NOT NULL REFERENCES socialraven.workspace(id) ON DELETE CASCADE,
    scope_type    VARCHAR(50)  NOT NULL,
    scope_value   VARCHAR(255) NOT NULL,
    approval_mode VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT workspace_approval_rule_unique
        UNIQUE (workspace_id, scope_type, scope_value),
    CONSTRAINT workspace_approval_rule_scope_type_check
        CHECK (scope_type IN ('ACCOUNT', 'CONTENT_TYPE')),
    CONSTRAINT workspace_approval_rule_mode_check
        CHECK (approval_mode IN ('NONE', 'OPTIONAL', 'REQUIRED', 'MULTI_STEP'))
);

CREATE INDEX idx_workspace_approval_rule_lookup
    ON socialraven.workspace_approval_rule(workspace_id, scope_type, scope_value);

ALTER TABLE socialraven.post_collection
    ADD COLUMN approval_mode_override VARCHAR(50);

ALTER TABLE socialraven.post_collection
    ADD CONSTRAINT post_collection_approval_mode_override_check
        CHECK (approval_mode_override IN ('NONE', 'OPTIONAL', 'REQUIRED', 'MULTI_STEP') OR approval_mode_override IS NULL);
