-- share_records – medical record sharing
CREATE TABLE share_records (
    id                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    medical_record_id BIGINT       NOT NULL                 COMMENT 'FK to medical_records.id',
    user_id           BIGINT       NOT NULL                 COMMENT 'FK to users.id (owner)',
    token             VARCHAR(64)  NOT NULL                 COMMENT 'Unique share token',
    expires_at        TIMESTAMP    NOT NULL                 COMMENT 'Token expiration time',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at        TIMESTAMP    NULL     DEFAULT NULL     COMMENT 'When the share was revoked',
    PRIMARY KEY (id),
    UNIQUE KEY uk_token (token),
    KEY idx_mr_id (medical_record_id),
    KEY idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Medical record share tokens';
