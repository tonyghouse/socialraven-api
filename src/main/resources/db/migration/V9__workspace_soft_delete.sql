-- V9: GDPR soft-delete for workspaces (§5.6)
-- Adds deleted_at to workspace.
-- WorkspaceDeletionScheduler hard-deletes rows where deleted_at < now() - 30 days.

ALTER TABLE socialraven.workspace ADD COLUMN deleted_at TIMESTAMPTZ;

-- Partial index — only indexes soft-deleted rows so the scheduler query is fast.
CREATE INDEX idx_workspace_deleted_at
    ON socialraven.workspace(deleted_at)
    WHERE deleted_at IS NOT NULL;
