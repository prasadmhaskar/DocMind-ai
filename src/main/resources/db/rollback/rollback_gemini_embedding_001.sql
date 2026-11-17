-- Run with the application stopped to restore the pre-switch vectors and statuses.
BEGIN;

DELETE FROM document_chunks current_chunks
USING document_chunks_embedding_backup old_chunks
WHERE current_chunks.document_id = old_chunks.document_id;

INSERT INTO document_chunks
    (id, document_id, chunk_index, content, created_at, page_number, embedding)
SELECT old_chunks.id,
       old_chunks.document_id,
       old_chunks.chunk_index,
       old_chunks.content,
       old_chunks.created_at,
       old_chunks.page_number,
       old_chunks.embedding
FROM document_chunks_embedding_backup old_chunks
JOIN documents ON documents.id = old_chunks.document_id;

UPDATE documents
SET document_status = backup.document_status,
    updated_at = CURRENT_TIMESTAMP
FROM document_embedding_model_status_backup backup
WHERE documents.id = backup.document_id;

DELETE FROM document_processing_jobs;

INSERT INTO document_processing_jobs
    (document_id, attempts, available_at, locked_until, last_error,
     rebuild_existing_chunks, fail_on_rebuild_failure)
SELECT documents.id, 0, CURRENT_TIMESTAMP, NULL, NULL, FALSE, FALSE
FROM documents
JOIN document_embedding_model_status_backup backup ON backup.document_id = documents.id
WHERE backup.document_status IN ('UPLOADING', 'PROCESSING')
ON CONFLICT (document_id) DO NOTHING;

UPDATE documents
SET document_status = 'PROCESSING',
    updated_at = CURRENT_TIMESTAMP
WHERE document_status = 'READY'
  AND NOT EXISTS (
      SELECT 1 FROM document_embedding_model_status_backup backup
      WHERE backup.document_id = documents.id
  )
  AND EXISTS (SELECT 1 FROM document_chunks WHERE document_id = documents.id);

INSERT INTO document_processing_jobs
    (document_id, attempts, available_at, locked_until, last_error,
     rebuild_existing_chunks, fail_on_rebuild_failure)
SELECT documents.id, 0, CURRENT_TIMESTAMP, NULL, NULL, TRUE, TRUE
FROM documents
WHERE document_status = 'PROCESSING'
  AND NOT EXISTS (
      SELECT 1 FROM document_embedding_model_status_backup backup
      WHERE backup.document_id = documents.id
  )
ON CONFLICT (document_id) DO UPDATE
SET rebuild_existing_chunks = TRUE,
    fail_on_rebuild_failure = FALSE,
    attempts = 0,
    available_at = CURRENT_TIMESTAMP,
    locked_until = NULL,
    last_error = NULL;

SELECT setval(
    pg_get_serial_sequence('document_chunks', 'id'),
    COALESCE((SELECT MAX(id) FROM document_chunks), 1),
    EXISTS (SELECT 1 FROM document_chunks)
);

COMMIT;

-- After confirming rollback, remove the backup tables when no longer needed:
-- DROP TABLE document_chunks_embedding_backup;
-- DROP TABLE document_embedding_model_status_backup;
