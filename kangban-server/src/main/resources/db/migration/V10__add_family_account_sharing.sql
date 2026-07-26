CREATE TABLE family_groups (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    owner_user_id BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMP    NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_fg_owner (owner_user_id),
    KEY idx_fg_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Account-based family groups';

CREATE TABLE family_group_members (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    family_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    relation   VARCHAR(20) DEFAULT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'member',
    status     VARCHAR(20) NOT NULL DEFAULT 'active',
    joined_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fgm_family_user (family_id, user_id),
    KEY idx_fgm_user_status (user_id, status),
    CONSTRAINT fk_fgm_family FOREIGN KEY (family_id) REFERENCES family_groups (id),
    CONSTRAINT fk_fgm_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Active account members of a family group';

CREATE TABLE family_invitations (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    family_id            BIGINT      NOT NULL,
    inviter_user_id      BIGINT      NOT NULL,
    invitee_user_id      BIGINT      NOT NULL,
    relation             VARCHAR(20) DEFAULT NULL,
    can_view_health      TINYINT(1)  NOT NULL DEFAULT 1,
    can_add_health       TINYINT(1)  NOT NULL DEFAULT 0,
    can_view_records     TINYINT(1)  NOT NULL DEFAULT 0,
    can_view_medications TINYINT(1)  NOT NULL DEFAULT 0,
    can_view_reports     TINYINT(1)  NOT NULL DEFAULT 1,
    can_use_ai           TINYINT(1)  NOT NULL DEFAULT 0,
    can_modify           TINYINT(1)  NOT NULL DEFAULT 0,
    can_delete           TINYINT(1)  NOT NULL DEFAULT 0,
    status               VARCHAR(20) NOT NULL DEFAULT 'pending',
    expires_at           TIMESTAMP   NOT NULL,
    responded_at         TIMESTAMP   NULL DEFAULT NULL,
    created_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_fi_invitee_status (invitee_user_id, status),
    KEY idx_fi_inviter_status (inviter_user_id, status),
    KEY idx_fi_family (family_id),
    CONSTRAINT fk_fi_family FOREIGN KEY (family_id) REFERENCES family_groups (id),
    CONSTRAINT fk_fi_inviter FOREIGN KEY (inviter_user_id) REFERENCES users (id),
    CONSTRAINT fk_fi_invitee FOREIGN KEY (invitee_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Explicit family data-sharing invitations';

CREATE TABLE family_permissions (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    family_id            BIGINT      NOT NULL,
    subject_user_id      BIGINT      NOT NULL,
    grantee_user_id      BIGINT      NOT NULL,
    can_view_health      TINYINT(1)  NOT NULL DEFAULT 0,
    can_add_health       TINYINT(1)  NOT NULL DEFAULT 0,
    can_view_records     TINYINT(1)  NOT NULL DEFAULT 0,
    can_view_medications TINYINT(1)  NOT NULL DEFAULT 0,
    can_view_reports     TINYINT(1)  NOT NULL DEFAULT 0,
    can_use_ai           TINYINT(1)  NOT NULL DEFAULT 0,
    can_modify           TINYINT(1)  NOT NULL DEFAULT 0,
    can_delete           TINYINT(1)  NOT NULL DEFAULT 0,
    status               VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    revoked_at           TIMESTAMP   NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fp_family_subject_grantee (family_id, subject_user_id, grantee_user_id),
    KEY idx_fp_grantee_status (grantee_user_id, status),
    KEY idx_fp_subject_status (subject_user_id, status),
    CONSTRAINT fk_fp_family FOREIGN KEY (family_id) REFERENCES family_groups (id),
    CONSTRAINT fk_fp_subject FOREIGN KEY (subject_user_id) REFERENCES users (id),
    CONSTRAINT fk_fp_grantee FOREIGN KEY (grantee_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Consent-based account health data permissions';

ALTER TABLE chat_sessions
    ADD COLUMN subject_user_id BIGINT DEFAULT NULL
        COMMENT 'Shared account being consulted; NULL means session owner'
        AFTER user_id,
    ADD INDEX idx_cs_subject_user_id (subject_user_id);
