ALTER TABLE chat_messages
    ADD COLUMN reply_to_message_id BIGINT DEFAULT NULL
        COMMENT 'User message answered by this assistant message'
        AFTER attachment_url,
    ADD COLUMN client_message_id VARCHAR(64) DEFAULT NULL
        COMMENT 'Client-generated idempotency key'
        AFTER reply_to_message_id;

CREATE UNIQUE INDEX uk_cm_reply_to_message_id
    ON chat_messages (reply_to_message_id);

CREATE UNIQUE INDEX uk_cm_user_client_message_id
    ON chat_messages (user_id, client_message_id);
