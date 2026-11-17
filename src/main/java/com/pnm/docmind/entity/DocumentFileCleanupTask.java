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
@Table(name = "document_file_cleanup")
@Getter
@NoArgsConstructor
public class DocumentFileCleanupTask {

    @Id
    @Column(name = "stored_filename", nullable = false, length = 255)
    private String storedFilename;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public DocumentFileCleanupTask(String storedFilename) {
        this.storedFilename = storedFilename;
    }
}
