package com.pnm.docmind.repository;

import com.pnm.docmind.dto.UploadDocumentResponse;
import com.pnm.docmind.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("""
SELECT new com.pnm.docmind.dto.UploadDocumentResponse(
d.id,
d.documentStatus
)
FROM Document d
WHERE d.id = :documentId
""")
    UploadDocumentResponse findByDocumentId(@Param("documentId") String documentId);
}