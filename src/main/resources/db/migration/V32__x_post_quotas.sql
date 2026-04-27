ALTER TABLE socialraven.plan_config
    ADD COLUMN x_posts_per_month INTEGER;

UPDATE socialraven.plan_config
SET x_posts_per_month = CASE plan_type
    WHEN 'INFLUENCER_TRIAL' THEN 20
    WHEN 'INFLUENCER_BASE'  THEN 150
    WHEN 'INFLUENCER_PRO'   THEN 300
    WHEN 'AGENCY_TRIAL'     THEN 50
    WHEN 'AGENCY_BASE'      THEN 300
    WHEN 'AGENCY_PRO'       THEN 300
    WHEN 'AGENCY_CUSTOM'    THEN 300
    ELSE x_posts_per_month
END
WHERE x_posts_per_month IS NULL;

ALTER TABLE socialraven.company_plan
    ADD COLUMN custom_x_posts_limit INTEGER;

ALTER TABLE socialraven.workspace
    ADD COLUMN custom_x_posts_limit INTEGER;
