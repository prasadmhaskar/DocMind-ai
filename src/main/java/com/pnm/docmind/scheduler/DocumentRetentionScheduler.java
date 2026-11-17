package com.pnm.docmind.scheduler;

import com.pnm.docmind.entity.DocumentFileCleanupTask;
import com.pnm.docmind.repository.DocumentFileCleanupRepository;
import com.pnm.docmind.repository.DocumentRepository;
import com.pnm.docmind.service.document.DocumentFileCleanupService;
import com.pnm.docmind.service.document.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentRetentionScheduler {

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final DocumentFileCleanupRepository cleanupRepository;
    private final DocumentFileCleanupService cleanupService;

    @Value("${app.document-retention.days:5}")
    private long retentionDays;

    @PostConstruct
    void validateRetentionConfiguration() {
        if (retentionDays < 1) {
            throw new IllegalStateException("app.document-retention.days must be at least 1");
        }
    }

    @Scheduled(
            fixedDelayString = "${app.document-retention.scan-interval-ms:86400000}",
            initialDelayString = "${app.document-retention.initial-delay-ms:60000}"
    )
    public void removeExpiredDocumentsAndRetryFileCleanup() {
        retryPendingFileCleanup();

        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        documentRepository.findTop100ByRetentionExemptFalseAndUploadedAtBeforeOrderByUploadedAtAsc(cutoff)
                .forEach(document -> {
                    try {
                        documentService.deleteExpiredDocument(document.getId());
                    } catch (RuntimeException exception) {
                        log.error("Failed to delete expired document id={}", document.getId(), exception);
                    }
                });
    }

    private void retryPendingFileCleanup() {
        for (DocumentFileCleanupTask task : cleanupRepository.findTop100ByOrderByCreatedAtAsc()) {
            try {
                cleanupService.deletePendingFile(task.getStoredFilename());
            } catch (RuntimeException exception) {
                log.warn("Stored file cleanup will be retried for {}", task.getStoredFilename(), exception);
            }
        }
    }
}
