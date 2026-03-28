-- Remove title column from post_collection.
-- Description is the single content field for all platforms; YouTube title lives in platform_configs JSONB.

ALTER TABLE socialraven.post_collection DROP COLUMN title;
