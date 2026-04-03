CREATE TABLE socialraven.post_collection_collaboration_thread (
    id                      BIGSERIAL      PRIMARY KEY,
    post_collection_id      BIGINT         NOT NULL REFERENCES socialraven.post_collection(id) ON DELETE CASCADE,
    workspace_id            VARCHAR(255)   NOT NULL REFERENCES socialraven.workspace(id) ON DELETE CASCADE,
    thread_type             VARCHAR(50)    NOT NULL,
    visibility              VARCHAR(50)    NOT NULL DEFAULT 'INTERNAL',
    status                  VARCHAR(50)    NOT NULL DEFAULT 'OPEN',
    author_user_id          VARCHAR(255)   NOT NULL,
    body                    VARCHAR(4000),
    mentioned_user_ids      JSONB          NOT NULL DEFAULT '[]'::jsonb,
    anchor_start            INTEGER,
    anchor_end              INTEGER,
    anchor_text             TEXT,
    suggested_text          TEXT,
    suggestion_status       VARCHAR(50),
    suggestion_decided_by   VARCHAR(255),
    suggestion_decided_at   TIMESTAMPTZ(6),
    resolved_by_user_id     VARCHAR(255),
    resolved_at             TIMESTAMPTZ(6),
    created_at              TIMESTAMPTZ(6) NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ(6) NOT NULL DEFAULT NOW(),
    CONSTRAINT post_collection_collaboration_thread_type_check
        CHECK (thread_type IN ('COMMENT', 'NOTE', 'SUGGESTION')),
    CONSTRAINT post_collection_collaboration_visibility_check
        CHECK (visibility IN ('INTERNAL', 'CLIENT_VISIBLE')),
    CONSTRAINT post_collection_collaboration_status_check
        CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT post_collection_collaboration_suggestion_status_check
        CHECK (suggestion_status IN ('PENDING', 'ACCEPTED', 'REJECTED') OR suggestion_status IS NULL),
    CONSTRAINT post_collection_collaboration_anchor_range_check
        CHECK (
            (anchor_start IS NULL AND anchor_end IS NULL)
            OR (anchor_start IS NOT NULL AND anchor_end IS NOT NULL AND anchor_start >= 0 AND anchor_end >= anchor_start)
        )
);

CREATE INDEX idx_post_collection_collaboration_thread_lookup
    ON socialraven.post_collection_collaboration_thread(post_collection_id, status, updated_at DESC);

CREATE TABLE socialraven.post_collection_collaboration_reply (
    id                  BIGSERIAL      PRIMARY KEY,
    thread_id           BIGINT         NOT NULL REFERENCES socialraven.post_collection_collaboration_thread(id) ON DELETE CASCADE,
    author_user_id      VARCHAR(255)   NOT NULL,
    body                VARCHAR(4000)  NOT NULL,
    mentioned_user_ids  JSONB          NOT NULL DEFAULT '[]'::jsonb,
    created_at          TIMESTAMPTZ(6) NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ(6) NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_post_collection_collaboration_reply_lookup
    ON socialraven.post_collection_collaboration_reply(thread_id, created_at);
