package com.pnm.docmind.service.document;

import com.pnm.docmind.constant.DocumentStatus;
import com.pnm.docmind.entity.Document;
import com.pnm.docmind.entity.DocumentFileCleanupTask;
import com.pnm.docmind.entity.DocumentProcessingJob;
import com.pnm.docmind.repository.DocumentFileCleanupRepository;
import com.pnm.docmind.repository.DocumentProcessingJobRepository;
import com.pnm.docmind.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingJobService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LEASE_MINUTES = 15;

    private final JdbcTemplate jdbcTemplate;
    private final DocumentProcessingJobRepository jobRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFileCleanupRepository fileCleanupRepository;
    private final DocumentFileCleanupService fileCleanupService;
    private final Set<Long> activeJobs = ConcurrentHashMap.newKeySet();

    @Transactional
    public List<Long> claimAvailableJobs(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        String sql = """
                WITH candidates AS (
                    SELECT jobs.document_id
                    FROM document_processing_jobs jobs
                    JOIN documents docs ON docs.id = jobs.document_id
                    WHERE (docs.document_status IN ('UPLOADING', 'PROCESSING')
                           OR jobs.rebuild_existing_chunks = TRUE)
                      AND jobs.available_at <= CURRENT_TIMESTAMP
                      AND (jobs.locked_until IS NULL OR jobs.locked_until <= CURRENT_TIMESTAMP)
                    ORDER BY jobs.available_at, jobs.created_at
                    LIMIT ?
                    FOR UPDATE OF jobs SKIP LOCKED
                )
                UPDATE document_processing_jobs jobs
                SET attempts = jobs.attempts + 1,
                    locked_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 minute'),
                    last_error = NULL
                FROM candidates
                WHERE jobs.document_id = candidates.document_id
                RETURNING jobs.document_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("document_id"), limit, LEASE_MINUTES);
    }

    public void markActive(Long documentId) {
        activeJobs.add(documentId);
    }

    public void markInactive(Long documentId) {
        activeJobs.remove(documentId);
    }

    @Scheduled(fixedDelayString = "${app.document-processing.lease-heartbeat-ms:60000}")
    public void renewActiveLeases() {
        for (Long documentId : activeJobs) {
            jdbcTemplate.update("""
                    UPDATE document_processing_jobs
                    SET locked_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 minute')
                    WHERE document_id = ?
                    """, LEASE_MINUTES, documentId);
        }
    }

    @Transactional
    public void recordFailure(Long documentId, Exception failure) {
        DocumentProcessingJob job = jobRepository.findById(documentId).orElse(null);
        if (job == null) {
            return;
        }

        String error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        if (error.length() > 4000) {
            error = error.substring(0, 4000);
        }

        if (job.getAttempts() < MAX_ATTEMPTS) {
            long delayMinutes = Math.min(30, 1L << Math.max(0, job.getAttempts() - 1));
            jdbcTemplate.update("""
                    UPDATE document_processing_jobs
                    SET available_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 minute'),
                        locked_until = NULL,
                        last_error = ?
                    WHERE document_id = ?
                    """, delayMinutes, error, documentId);
            log.warn("Document processing will retry. documentId={}, attempt={}, retryDelayMinutes={}",
                    documentId, job.getAttempts(), delayMinutes);
            return;
        }

        if (job.isRebuildExistingChunks()) {
            if (job.isFailOnRebuildFailure()) {
                Document document = documentRepository.findById(documentId).orElse(null);
                if (document != null) {
                    document.setDocumentStatus(DocumentStatus.FAILED);
                    documentRepository.save(document);
                }
            }
            jobRepository.delete(job);
            log.error("Document reindex exhausted retries; kept existing chunks. documentId={}, attempts={}, failOnRebuildFailure={}",
                    documentId, job.getAttempts(), job.isFailOnRebuildFailure(), failure);
            return;
        }

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document != null) {
            document.setDocumentStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
            fileCleanupRepository.save(new DocumentFileCleanupTask(document.getStoredFilename()));
            scheduleStoredFileCleanup(document.getStoredFilename());
        }
        jobRepository.delete(job);
        log.error("Document processing exhausted retries. documentId={}, attempts={}",
                documentId, job.getAttempts(), failure);
    }

    private void scheduleStoredFileCleanup(String storedFilename) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    fileCleanupService.deletePendingFile(storedFilename);
                } catch (RuntimeException cleanupException) {
                    log.warn("Failed document file cleanup deferred for {}", storedFilename, cleanupException);
                }
            }
        });
    }
}
