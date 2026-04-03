CREATE TABLE socialraven.workspace_library_item (
    id                   BIGSERIAL      PRIMARY KEY,
    workspace_id         VARCHAR(255)   NOT NULL REFERENCES socialraven.workspace(id) ON DELETE CASCADE,
    item_type            VARCHAR(50)    NOT NULL,
    status               VARCHAR(50)    NOT NULL DEFAULT 'DRAFT',
    name                 VARCHAR(255)   NOT NULL,
    folder_name          VARCHAR(120),
    description          TEXT,
    body                 TEXT,
    snippet_target       VARCHAR(50),
    post_collection_type VARCHAR(50),
    tags                 JSONB          NOT NULL DEFAULT '[]'::jsonb,
    media_files          JSONB          NOT NULL DEFAULT '[]'::jsonb,
    platform_configs     JSONB,
    usage_notes          TEXT,
    internal_notes       TEXT,
    expires_at           TIMESTAMPTZ(6),
    created_by           VARCHAR(255)   NOT NULL,
    updated_by           VARCHAR(255)   NOT NULL,
    created_at           TIMESTAMPTZ(6) NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ(6) NOT NULL DEFAULT NOW(),
    CONSTRAINT workspace_library_item_type_check
        CHECK (item_type IN ('MEDIA_ASSET', 'SNIPPET', 'TEMPLATE')),
    CONSTRAINT workspace_library_item_status_check
        CHECK (status IN ('DRAFT', 'APPROVED', 'ARCHIVED')),
    CONSTRAINT workspace_library_item_snippet_target_check
        CHECK (snippet_target IN ('CAPTION', 'FIRST_COMMENT') OR snippet_target IS NULL),
    CONSTRAINT workspace_library_item_post_collection_type_check
        CHECK (post_collection_type IN ('IMAGE', 'VIDEO', 'TEXT') OR post_collection_type IS NULL)
);

CREATE INDEX idx_workspace_library_item_lookup
    ON socialraven.workspace_library_item(workspace_id, item_type, status, updated_at DESC);

CREATE TABLE socialraven.workspace_library_bundle (
    id             BIGSERIAL      PRIMARY KEY,
    workspace_id   VARCHAR(255)   NOT NULL REFERENCES socialraven.workspace(id) ON DELETE CASCADE,
    name           VARCHAR(255)   NOT NULL,
    description    TEXT,
    campaign_label VARCHAR(255),
    created_by     VARCHAR(255)   NOT NULL,
    updated_by     VARCHAR(255)   NOT NULL,
    created_at     TIMESTAMPTZ(6) NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ(6) NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workspace_library_bundle_lookup
    ON socialraven.workspace_library_bundle(workspace_id, updated_at DESC);

CREATE TABLE socialraven.workspace_library_bundle_item (
    id              BIGSERIAL PRIMARY KEY,
    bundle_id       BIGINT    NOT NULL REFERENCES socialraven.workspace_library_bundle(id) ON DELETE CASCADE,
    library_item_id BIGINT    NOT NULL REFERENCES socialraven.workspace_library_item(id) ON DELETE CASCADE,
    position        INTEGER   NOT NULL DEFAULT 0,
    CONSTRAINT workspace_library_bundle_item_unique
        UNIQUE (bundle_id, library_item_id),
    CONSTRAINT workspace_library_bundle_item_position_check
        CHECK (position >= 0)
);

CREATE INDEX idx_workspace_library_bundle_item_lookup
    ON socialraven.workspace_library_bundle_item(bundle_id, position, id);
