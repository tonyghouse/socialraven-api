-- Add status column to user_profile.
-- Existing rows default to ACTIVE (all current users are active).
ALTER TABLE socialraven.user_profile
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
