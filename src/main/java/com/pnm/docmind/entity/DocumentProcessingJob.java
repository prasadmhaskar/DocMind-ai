package com.pnm.docmind.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_processing_jobs")
@Getter
@NoArgsConstructor
public class DocumentProcessingJob {

    @Id
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "rebuild_existing_chunks", nullable = false)
    private boolean rebuildExistingChunks;

    @Column(name = "fail_on_rebuild_failure", nullable = false)
    private boolean failOnRebuildFailure;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public DocumentProcessingJob(Long documentId) {
        this.documentId = documentId;
        this.availableAt = LocalDateTime.now();
    }
}
