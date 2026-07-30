CREATE TABLE documents
(
    id BIGSERIAL PRIMARY KEY,

    original_filename VARCHAR(255) NOT NULL,

    stored_filename VARCHAR(255) NOT NULL UNIQUE,

    content_type VARCHAR(100) NOT NULL,

    file_size BIGINT NOT NULL,

    document_status VARCHAR(50) NOT NULL,

    uploaded_at TIMESTAMP NOT NULL,

    updated_at TIMESTAMP
);