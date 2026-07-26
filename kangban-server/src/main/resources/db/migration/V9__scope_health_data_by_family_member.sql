ALTER TABLE chat_sessions
    ADD COLUMN member_id BIGINT DEFAULT NULL
        COMMENT 'Selected family member; NULL means the account owner'
        AFTER user_id,
    ADD INDEX idx_cs_member_id (member_id);

ALTER TABLE medications
    ADD COLUMN member_id BIGINT DEFAULT NULL
        COMMENT 'Family member owner; NULL means the account owner'
        AFTER user_id,
    ADD INDEX idx_med_member_id (member_id);

ALTER TABLE medical_records
    ADD COLUMN member_id BIGINT DEFAULT NULL
        COMMENT 'Family member owner; NULL means the account owner'
        AFTER user_id,
    ADD INDEX idx_mr_member_id (member_id);
