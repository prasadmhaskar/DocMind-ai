UPDATE document_chunks
SET page_number = 1
WHERE page_number IS NULL;

ALTER TABLE document_chunks
    ALTER COLUMN page_number SET NOT NULL;
