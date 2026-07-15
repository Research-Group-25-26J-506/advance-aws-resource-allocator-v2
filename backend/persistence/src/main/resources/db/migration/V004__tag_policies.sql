CREATE TABLE tag_policies (
    id          BINARY(16)   NOT NULL,
    team_id     BINARY(16)   NULL,        -- null = platform-wide policy
    tag_key     VARCHAR(128) NOT NULL,
    required    BOOLEAN      NOT NULL DEFAULT TRUE,
    value_regex VARCHAR(512) NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_tag_policy (team_id, tag_key),
    CONSTRAINT fk_tag_policies_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
