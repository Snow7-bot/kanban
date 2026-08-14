ALTER TABLE chat_messages
    ADD COLUMN agent_tool_traces_json LONGTEXT DEFAULT NULL
        COMMENT 'Safe Agent tool execution traces for assistant messages'
        AFTER citations_json;
