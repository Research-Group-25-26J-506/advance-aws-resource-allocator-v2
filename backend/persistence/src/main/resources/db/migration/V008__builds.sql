-- Source-to-image builds: the platform triggers CodeBuild from a git repo and tracks status.
CREATE TABLE builds (
    id            BINARY(16)   NOT NULL,
    service_name  VARCHAR(63)  NOT NULL,
    repo          VARCHAR(512) NOT NULL,
    git_ref       VARCHAR(255) NOT NULL,
    image_tag     VARCHAR(255) NOT NULL,
    codebuild_id  VARCHAR(255) NULL,
    status        VARCHAR(40)  NOT NULL,   -- PENDING | IN_PROGRESS | SUCCEEDED | FAILED
    actor_id      VARCHAR(64)  NOT NULL,
    started_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at  TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY ix_builds_started (started_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
