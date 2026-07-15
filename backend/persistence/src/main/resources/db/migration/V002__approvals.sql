CREATE TABLE approvals (
    id           BINARY(16)   NOT NULL,
    request_id   BINARY(16)   NOT NULL,
    approver_id  VARCHAR(64)  NULL,       -- cognito sub; null while pending
    decision     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED
    comment      TEXT         NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    decided_at   TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_approvals_request (request_id),
    KEY ix_approvals_decision (decision, requested_at),
    CONSTRAINT fk_approvals_request FOREIGN KEY (request_id) REFERENCES requests (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
