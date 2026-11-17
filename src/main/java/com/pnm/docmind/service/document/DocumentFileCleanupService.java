package com.pnm.docmind.service.document;

import com.pnm.docmind.repository.DocumentFileCleanupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentFileCleanupService {

    private final DocumentFileCleanupRepository cleanupRepository;
    private final DeleteFailedDocument storedFileDeletion;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deletePendingFile(String storedFilename) {
        storedFileDeletion.deleteDocument(storedFilename);
        cleanupRepository.deleteById(storedFilename);
    }
}
