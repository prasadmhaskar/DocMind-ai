ALTER TABLE documents
    ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE conversations
    ADD COLUMN workspace_id UUID REFERENCES workspaces (id);

CREATE INDEX idx_conversations_document_workspace
    ON conversations (document_id, workspace_id, updated_at DESC);
