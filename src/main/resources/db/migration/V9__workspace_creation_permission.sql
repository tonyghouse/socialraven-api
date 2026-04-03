ALTER TABLE socialraven.user_profile
    ADD COLUMN can_create_workspaces BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE socialraven.user_profile up
SET can_create_workspaces = TRUE
WHERE EXISTS (
    SELECT 1
    FROM socialraven.company c
    WHERE c.owner_user_id = up.user_id
)
OR EXISTS (
    SELECT 1
    FROM socialraven.company_user cu
    WHERE cu.user_id = up.user_id
      AND cu.role IN ('OWNER', 'ADMIN')
);
