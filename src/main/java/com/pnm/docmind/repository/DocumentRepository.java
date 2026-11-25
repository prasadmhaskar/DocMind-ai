package com.pnm.docmind.repository;

import com.pnm.docmind.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    @Query("select d from Document d left join d.workspace w where w.id = :workspaceId or d.publicDocument = true order by d.uploadedAt desc")
    List<Document> findVisibleToWorkspace(@Param("workspaceId") UUID workspaceId);

    @Query("select d from Document d left join d.workspace w where d.id = :documentId and (w.id = :workspaceId or d.publicDocument = true)")
    Optional<Document> findVisibleByIdAndWorkspace(
            @Param("documentId") Long documentId,
            @Param("workspaceId") UUID workspaceId);

    @Query("select d from Document d left join fetch d.workspace where d.id = :documentId")
    Optional<Document> findByIdWithWorkspace(@Param("documentId") Long documentId);
    List<Document> findTop100ByRetentionExemptFalseAndUploadedAtBeforeOrderByUploadedAtAsc(LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id")
    Optional<Document> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id and d.workspace.id = :workspaceId")
    Optional<Document> findByIdAndWorkspaceIdForUpdate(
            @Param("id") Long id,
            @Param("workspaceId") UUID workspaceId);
}
