-- audit_log: append-only, deliberately NO foreign keys — audit rows must outlive their subjects.
CREATE TABLE audit_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id     VARCHAR(64)  NOT NULL,
    actor_email  VARCHAR(255) NULL,
    action       VARCHAR(80)  NOT NULL,   -- e.g. REQUEST_SUBMITTED, SYNC_TRIGGERED, APPROVAL_GRANTED
    subject_type VARCHAR(40)  NOT NULL,
    subject_id   VARCHAR(64)  NOT NULL,
    detail_json  JSON         NULL,
    trace_id     VARCHAR(32)  NULL,
    occurred_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_audit_subject (subject_type, subject_id, occurred_at),
    KEY ix_audit_actor (actor_id, occurred_at),
    CONSTRAINT ck_audit_detail_json CHECK (detail_json IS NULL OR JSON_VALID(detail_json))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
