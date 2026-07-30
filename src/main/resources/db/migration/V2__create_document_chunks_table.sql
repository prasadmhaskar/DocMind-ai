CREATE TABLE document_chunks
(
    id BIGSERIAL PRIMARY KEY,

    document_id BIGINT NOT NULL,

    chunk_index INT NOT NULL,

    content TEXT NOT NULL,

    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_document_chunk_document
        FOREIGN KEY(document_id)
            REFERENCES documents(id)
            ON DELETE CASCADE
);