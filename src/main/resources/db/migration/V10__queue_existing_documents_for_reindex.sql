ALTER TABLE document_processing_jobs
    ADD COLUMN rebuild_existing_chunks BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO document_processing_jobs (document_id, rebuild_existing_chunks)
SELECT DISTINCT documents.id, TRUE
FROM documents
JOIN document_chunks ON document_chunks.document_id = documents.id
WHERE documents.document_status = 'READY'
ON CONFLICT (document_id) DO UPDATE
SET rebuild_existing_chunks = TRUE,
    attempts = 0,
    available_at = CURRENT_TIMESTAMP,
    locked_until = NULL,
    last_error = NULL;
