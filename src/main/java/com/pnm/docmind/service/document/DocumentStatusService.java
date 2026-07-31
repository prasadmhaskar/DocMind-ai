package com.pnm.docmind.service.document;

import com.pnm.docmind.constant.DocumentStatus;
import com.pnm.docmind.entity.Document;
import com.pnm.docmind.exception.DocumentNotFoundException;
import com.pnm.docmind.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentStatusService {

    private final DocumentRepository documentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long documentId) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() ->
                        new DocumentNotFoundException("Document not found with id: " + documentId)
                );

        document.setDocumentStatus(DocumentStatus.FAILED);

        documentRepository.save(document);

        log.info("Document marked as FAILED. documentId={}", documentId);
    }
}
