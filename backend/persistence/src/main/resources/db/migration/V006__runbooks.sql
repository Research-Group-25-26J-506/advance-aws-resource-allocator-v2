CREATE TABLE runbooks (
    id             VARCHAR(80)  NOT NULL,   -- slug, e.g. 'stack-create-failed'
    path_in_repo   VARCHAR(512) NOT NULL,
    title          VARCHAR(255) NOT NULL,
    severity       VARCHAR(20)  NOT NULL,   -- SEV1..SEV4
    alerts_covered JSON         NULL,
    updated_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_runbooks_alerts_json CHECK (alerts_covered IS NULL OR JSON_VALID(alerts_covered))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- incidents: written by the rollback workflow (7.12) — created here so it isn't invented ad hoc.
CREATE TABLE incidents (
    id           BINARY(16)   NOT NULL,
    title        VARCHAR(255) NOT NULL,
    severity     VARCHAR(20)  NOT NULL,
    source       VARCHAR(40)  NOT NULL,    -- ROLLBACK_WORKFLOW | ALERT | MANUAL
    detail_json  JSON         NULL,
    opened_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at  TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY ix_incidents_opened (opened_at),
    CONSTRAINT ck_incidents_detail_json CHECK (detail_json IS NULL OR JSON_VALID(detail_json))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
