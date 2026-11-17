CREATE TABLE workspaces
(
    id UUID PRIMARY KEY,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL
);

ALTER TABLE documents
    ADD COLUMN workspace_id UUID REFERENCES workspaces (id);

CREATE INDEX idx_documents_workspace_uploaded_at
    ON documents (workspace_id, uploaded_at DESC);
