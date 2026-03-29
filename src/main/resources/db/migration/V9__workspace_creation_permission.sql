ALTER TABLE socialraven.user_profile
    ADD COLUMN can_create_workspaces BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE socialraven.user_profile up
SET can_create_workspaces = TRUE
WHERE EXISTS (
    SELECT 1
    FROM socialraven.workspace w
    WHERE w.owner_user_id = up.user_id
)
OR EXISTS (
    SELECT 1
    FROM socialraven.workspace_member wm
    WHERE wm.user_id = up.user_id
      AND wm.role IN ('OWNER', 'ADMIN')
);
