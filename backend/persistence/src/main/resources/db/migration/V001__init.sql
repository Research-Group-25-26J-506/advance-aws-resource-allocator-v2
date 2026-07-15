-- V001: core schema. PKs are BINARY(16) UUIDv7 (time-ordered); BIGINT auto-increment only for
-- append-only high-volume tables. Never edit an applied migration — forward-fix only.

CREATE TABLE teams (
    id            BINARY(16)   NOT NULL,
    name          VARCHAR(120) NOT NULL,
    cost_center   VARCHAR(40)  NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_teams_name (name)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id            BINARY(16)   NOT NULL,
    cognito_sub   VARCHAR(64)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    display_name  VARCHAR(120) NULL,
    team_id       BINARY(16)   NULL,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_sub (cognito_sub),
    UNIQUE KEY uq_users_email (email),
    CONSTRAINT fk_users_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id       BINARY(16)  NOT NULL,
    role          VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE templates (
    id            VARCHAR(64)   NOT NULL,  -- ^[a-z][a-z0-9-]{1,63}$, e.g. 's3-bucket'
    display_name  VARCHAR(120)  NOT NULL,
    description   VARCHAR(1024) NOT NULL,
    category      VARCHAR(40)   NOT NULL,
    maturity      VARCHAR(20)   NOT NULL DEFAULT 'beta',
    application   VARCHAR(120)  NULL,
    created_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE template_versions (
    id              BINARY(16)   NOT NULL,
    template_id     VARCHAR(64)  NOT NULL,
    version         VARCHAR(20)  NOT NULL,   -- semver
    commit_sha      VARCHAR(40)  NOT NULL,
    s3_key_body     VARCHAR(512) NOT NULL,
    s3_key_schema   VARCHAR(512) NOT NULL,
    s3_key_manifest VARCHAR(512) NOT NULL,
    status          VARCHAR(20)  NOT NULL,   -- DRAFT | PUBLISHED | DEPRECATED; PUBLISHED is immutable
    published_at    TIMESTAMP(6) NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_template_version (template_id, version),
    CONSTRAINT fk_versions_template FOREIGN KEY (template_id) REFERENCES templates (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE requests (
    id                  BINARY(16)   NOT NULL,
    template_version_id BINARY(16)   NOT NULL,
    template_id         VARCHAR(64)  NOT NULL,
    requester_id        VARCHAR(64)  NOT NULL,   -- cognito sub
    requester_email     VARCHAR(255) NOT NULL,
    team_id             BINARY(16)   NOT NULL,
    environment         VARCHAR(10)  NOT NULL,   -- DEV | STG | PROD
    region              VARCHAR(30)  NOT NULL,
    resource_name       VARCHAR(120) NOT NULL,
    status              VARCHAR(40)  NOT NULL,
    form_data_json      JSON         NOT NULL,
    idempotency_key     VARCHAR(40)  NOT NULL,
    stack_id            VARCHAR(512) NULL,
    failure_reason      TEXT         NULL,
    submitted_at        TIMESTAMP(6) NOT NULL,
    updated_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_requests_idem (idempotency_key),
    KEY ix_requests_requester_status (requester_id, status),
    KEY ix_requests_env_status (environment, status),
    KEY ix_requests_submitted (submitted_at),
    CONSTRAINT fk_requests_version FOREIGN KEY (template_version_id) REFERENCES template_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_requests_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE RESTRICT,
    CONSTRAINT ck_requests_form_json CHECK (JSON_VALID(form_data_json))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE request_events (
    id           BIGINT       NOT NULL AUTO_INCREMENT,  -- append-only, high volume
    request_id   BINARY(16)   NOT NULL,
    from_status  VARCHAR(40)  NULL,
    to_status    VARCHAR(40)  NOT NULL,
    reason       TEXT         NULL,
    source       VARCHAR(20)  NOT NULL,  -- PLATFORM | CLOUDFORMATION
    occurred_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_request_events (request_id, occurred_at),
    CONSTRAINT fk_events_request FOREIGN KEY (request_id) REFERENCES requests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE resource_outputs (
    id          BINARY(16)   NOT NULL,
    request_id  BINARY(16)   NOT NULL,
    output_key  VARCHAR(255) NOT NULL,
    output_value TEXT        NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_outputs (request_id, output_key),
    CONSTRAINT fk_outputs_request FOREIGN KEY (request_id) REFERENCES requests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
