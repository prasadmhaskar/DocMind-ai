ALTER TABLE document_processing_jobs
    ADD COLUMN fail_on_rebuild_failure BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE document_embedding_model_status_backup
(
    document_id BIGINT PRIMARY KEY REFERENCES documents(id) ON DELETE CASCADE,
    document_status VARCHAR(50) NOT NULL
);

INSERT INTO document_embedding_model_status_backup (document_id, document_status)
SELECT id, document_status
FROM documents;

CREATE TABLE document_chunks_embedding_backup
(
    id BIGINT PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    page_number INT NOT NULL,
    embedding vector(768) NOT NULL
);

INSERT INTO document_chunks_embedding_backup
    (id, document_id, chunk_index, content, created_at, page_number, embedding)
SELECT id, document_id, chunk_index, content, created_at, page_number, embedding
FROM document_chunks;

UPDATE documents
SET document_status = 'PROCESSING',
    updated_at = CURRENT_TIMESTAMP
WHERE document_status = 'READY'
  AND EXISTS (SELECT 1 FROM document_chunks WHERE document_id = documents.id);

INSERT INTO document_processing_jobs (document_id, rebuild_existing_chunks, fail_on_rebuild_failure)
SELECT documents.id, TRUE, TRUE
FROM documents
WHERE document_status = 'PROCESSING'
  AND EXISTS (SELECT 1 FROM document_chunks WHERE document_id = documents.id)
ON CONFLICT (document_id) DO UPDATE
SET rebuild_existing_chunks = TRUE,
    fail_on_rebuild_failure = TRUE,
    attempts = 0,
    available_at = CURRENT_TIMESTAMP,
    locked_until = NULL,
    last_error = NULL;
