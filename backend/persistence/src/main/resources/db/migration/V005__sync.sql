CREATE TABLE template_syncs (
    id           BINARY(16)   NOT NULL,
    actor_id     VARCHAR(64)  NOT NULL,
    commit_sha   VARCHAR(40)  NOT NULL,
    branch       VARCHAR(255) NOT NULL,
    mode         ENUM('APPLY','DRY_RUN') NOT NULL,
    status       VARCHAR(40)  NOT NULL,   -- SYNC_PENDING | SYNC_VALIDATING | SYNC_UPLOADING | SYNC_COMPLETE | SYNC_FAILED
    started_at   TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    summary_json JSON         NULL,
    error_json   JSON         NULL,
    PRIMARY KEY (id),
    KEY ix_syncs_started (started_at),
    CONSTRAINT ck_syncs_summary_json CHECK (summary_json IS NULL OR JSON_VALID(summary_json)),
    CONSTRAINT ck_syncs_error_json CHECK (error_json IS NULL OR JSON_VALID(error_json))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE sync_events (
    id          BIGINT       NOT NULL AUTO_INCREMENT,  -- append-only, like request_events
    sync_id     BINARY(16)   NOT NULL,
    from_status VARCHAR(40)  NULL,
    to_status   VARCHAR(40)  NOT NULL,
    detail      TEXT         NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_sync_events (sync_id, occurred_at),
    CONSTRAINT fk_sync_events_sync FOREIGN KEY (sync_id) REFERENCES template_syncs (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- SQL-side fallback lock table (3.10). DynamoDB platform-locks is the primary; this exists so
-- single-DB local dev can exercise the same code path.
CREATE TABLE distributed_locks (
    name        VARCHAR(120) NOT NULL,
    holder      VARCHAR(255) NOT NULL,
    acquired_at TIMESTAMP(6) NOT NULL,
    expires_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
