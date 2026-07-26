ALTER TABLE refresh_tokens
    ADD COLUMN revoked TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '0=有效，1=已撤销'
    AFTER token;
