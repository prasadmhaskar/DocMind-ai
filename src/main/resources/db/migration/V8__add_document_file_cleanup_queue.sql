CREATE TABLE document_file_cleanup
(
    stored_filename VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_uploaded_at ON documents(uploaded_at);
