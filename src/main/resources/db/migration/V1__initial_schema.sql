CREATE SCHEMA IF NOT EXISTS socialraven;

CREATE TABLE socialraven.post_collection (
  id BIGSERIAL PRIMARY KEY,
  description VARCHAR(100000) NOT NULL,
  post_collection_status VARCHAR(50) NOT NULL,
  post_collection_type VARCHAR(50) NOT NULL,
  scheduled_time TIMESTAMPTZ(6),
  title VARCHAR(1000) NOT NULL,
  user_id VARCHAR(255) NOT NULL
);

ALTER TABLE socialraven.post_collection
    ADD CONSTRAINT post_collection_status_check
        CHECK (post_collection_status IN ('SCHEDULED','SUCCESS','PARTIAL_SUCCESS','FAILED'));

ALTER TABLE socialraven.post_collection
    ADD CONSTRAINT post_collection_type_check
        CHECK (post_collection_type IN ('IMAGE','VIDEO','TEXT'));

CREATE TABLE socialraven.oauth_info (
  id BIGSERIAL PRIMARY KEY,
  access_token VARCHAR(10000) NOT NULL,
  additional_info JSONB NOT NULL,
  expires_at BIGINT NOT NULL,
  expires_at_utc TIMESTAMPTZ(6) NOT NULL,
  provider VARCHAR(50) NOT NULL,
  provider_user_id VARCHAR(255) NOT NULL,
  user_id VARCHAR(255) NOT NULL
);

ALTER TABLE socialraven.oauth_info
    ADD CONSTRAINT oauth_info_provider_check
        CHECK (provider IN ('INSTAGRAM','X','LINKEDIN','FACEBOOK','YOUTUBE','TIKTOK','THREADS'));

CREATE TABLE socialraven.post (
  id BIGSERIAL PRIMARY KEY,
  created_at TIMESTAMPTZ(6),
  post_status VARCHAR(50) NOT NULL,
  post_type VARCHAR(50) NOT NULL,
  provider VARCHAR(50) NOT NULL,
  provider_user_id VARCHAR(1000) NOT NULL,
  scheduled_time TIMESTAMPTZ(6),
  updated_at TIMESTAMPTZ(6),
  post_collection_id BIGINT NOT NULL
);

ALTER TABLE socialraven.post
    ADD CONSTRAINT post_post_status_check
        CHECK (post_status IN ('SCHEDULED','POSTED','FAILED'));

ALTER TABLE socialraven.post
    ADD CONSTRAINT post_post_type_check
        CHECK (post_type IN ('IMAGE','VIDEO','TEXT'));

ALTER TABLE socialraven.post
    ADD CONSTRAINT post_provider_check
        CHECK (provider IN ('INSTAGRAM','X','LINKEDIN','FACEBOOK','YOUTUBE','TIKTOK','THREADS'));

ALTER TABLE socialraven.post
    ADD CONSTRAINT fk_post_collection
        FOREIGN KEY (post_collection_id)
            REFERENCES socialraven.post_collection(id);

CREATE TABLE socialraven.post_media (
  id BIGSERIAL PRIMARY KEY,
  file_key VARCHAR(255) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(255) NOT NULL,
  size BIGINT NOT NULL,
  post_collection_id BIGINT NOT NULL
);

ALTER TABLE socialraven.post_media
    ADD CONSTRAINT fk_post_media_collection
        FOREIGN KEY (post_collection_id)
            REFERENCES socialraven.post_collection(id);