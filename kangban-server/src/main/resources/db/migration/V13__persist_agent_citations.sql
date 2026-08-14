ALTER TABLE chat_messages
    ADD COLUMN citations_json LONGTEXT DEFAULT NULL
        COMMENT 'Structured public/private RAG citations for assistant messages'
        AFTER client_message_id;
