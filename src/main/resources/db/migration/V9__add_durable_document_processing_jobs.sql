CREATE TABLE document_processing_jobs
(
    document_id BIGINT PRIMARY KEY,
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_processing_job_document
        FOREIGN KEY (document_id)
            REFERENCES documents(id)
            ON DELETE CASCADE
);

CREATE INDEX idx_document_processing_jobs_available
    ON document_processing_jobs(available_at, created_at);

INSERT INTO document_processing_jobs (document_id)
SELECT id
FROM documents
WHERE document_status IN ('UPLOADING', 'PROCESSING');
