package com.pnm.docmind.scheduler;

import com.pnm.docmind.service.document.DocumentProcessingJobService;
import com.pnm.docmind.service.document.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DocumentProcessingScheduler {

    private final DocumentProcessingJobService jobService;
    private final DocumentProcessingService processingService;
    private final ThreadPoolTaskExecutor processingExecutor;

    public DocumentProcessingScheduler(
            DocumentProcessingJobService jobService,
            DocumentProcessingService processingService,
            @Qualifier("documentProcessingExecutor") ThreadPoolTaskExecutor processingExecutor) {
        this.jobService = jobService;
        this.processingService = processingService;
        this.processingExecutor = processingExecutor;
    }

    @Scheduled(
            fixedDelayString = "${app.document-processing.poll-interval-ms:5000}",
            initialDelayString = "${app.document-processing.initial-delay-ms:5000}"
    )
    public void dispatchPendingJobs() {
        int availableSlots = processingExecutor.getMaxPoolSize() - processingExecutor.getActiveCount();
        if (availableSlots <= 0) {
            return;
        }

        for (Long documentId : jobService.claimAvailableJobs(availableSlots)) {
            try {
                processingService.processDocumentAsync(documentId);
            } catch (RuntimeException dispatchFailure) {
                // The persisted job remains leased and becomes claimable after its lease expires.
                log.error("Could not dispatch document processing job. documentId={}", documentId, dispatchFailure);
            }
        }
    }
}
