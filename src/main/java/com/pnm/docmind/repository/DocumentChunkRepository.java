package com.pnm.docmind.repository;

import com.pnm.docmind.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    @Modifying
    @Query("delete from DocumentChunk chunk where chunk.document.id = :documentId")
    int deleteByDocumentId(@Param("documentId") Long documentId);

}
