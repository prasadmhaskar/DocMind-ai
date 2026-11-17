CREATE TABLE conversations
(
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_conversation_document
        FOREIGN KEY (document_id)
            REFERENCES documents(id)
            ON DELETE CASCADE
);

CREATE INDEX idx_conversations_document_id
    ON conversations(document_id);

CREATE TABLE chat_messages
(
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_conversation
        FOREIGN KEY (conversation_id)
            REFERENCES conversations(id)
            ON DELETE CASCADE,
    CONSTRAINT chk_chat_message_role
        CHECK (role IN ('USER', 'ASSISTANT'))
);

CREATE INDEX idx_chat_messages_conversation_id_id
    ON chat_messages(conversation_id, id DESC);
