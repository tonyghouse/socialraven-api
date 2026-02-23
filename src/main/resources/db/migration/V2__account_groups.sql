CREATE TABLE socialraven.account_group (
  id         BIGSERIAL    PRIMARY KEY,
  user_id    VARCHAR(255) NOT NULL,
  name       VARCHAR(255) NOT NULL,
  color      VARCHAR(20)  NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE socialraven.account_group_member (
  group_id         BIGINT       NOT NULL,
  provider_user_id VARCHAR(255) NOT NULL,
  PRIMARY KEY (group_id, provider_user_id),
  CONSTRAINT fk_account_group_member_group
    FOREIGN KEY (group_id)
      REFERENCES socialraven.account_group(id)
      ON DELETE CASCADE
);
