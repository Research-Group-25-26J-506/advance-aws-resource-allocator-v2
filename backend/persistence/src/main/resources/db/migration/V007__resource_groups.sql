-- Resource groups become first-class: created explicitly, then resources attach by using the
-- group name as their resource name (name remains the join key; a group_id FK on requests can
-- follow once memberships need to diverge from naming).
CREATE TABLE resource_groups (
    id          BINARY(16)   NOT NULL,
    name        VARCHAR(63)  NOT NULL,
    description VARCHAR(512) NULL,
    created_by  VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_resource_groups_name (name)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
