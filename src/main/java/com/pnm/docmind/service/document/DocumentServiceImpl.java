package com.pnm.docmind.service.document;

import com.pnm.docmind.constant.DocumentStatus;
import com.pnm.docmind.dto.UploadDocumentResponse;
import com.pnm.docmind.dto.DocumentSummaryResponse;
import com.pnm.docmind.entity.Document;
import com.pnm.docmind.entity.DocumentFileCleanupTask;
import com.pnm.docmind.entity.DocumentProcessingJob;
import com.pnm.docmind.exception.EmptyFile;
import com.pnm.docmind.exception.FileStorageException;
import com.pnm.docmind.exception.InvalidFile;
import com.pnm.docmind.exception.DocumentNotFoundException;
import com.pnm.docmind.repository.DocumentRepository;
import com.pnm.docmind.repository.DocumentFileCleanupRepository;
import com.pnm.docmind.repository.DocumentProcessingJobRepository;
import com.pnm.docmind.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.InputStream;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentFileCleanupRepository fileCleanupRepository;
    private final DocumentFileCleanupService fileCleanupService;
    private final DocumentProcessingJobRepository processingJobRepository;
    private final WorkspaceRepository workspaceRepository;

    @Value("${app.storage.path}")
    private String storagePath;

    @Override
    @Transactional
    public UploadDocumentResponse upload(MultipartFile file, UUID workspaceId) {

        if (file.isEmpty() || file.getSize() == 0) {
            throw new EmptyFile("File is empty");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());

        if(originalFilename.contains("..") || originalFilename.isBlank()){
            throw new InvalidFile("Invalid file name or file type");
        }

        String fileContentType = "application/pdf";

        log.info("Uploading file {}", originalFilename);

        String storedFileName = UUID.randomUUID() + ".pdf";

        Path uploadPath = Paths.get(storagePath);
        Path filePath = uploadPath.resolve(storedFileName);
        try {
            Files.createDirectories(uploadPath);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            deleteIfPresent(filePath);
            throw new FileStorageException("Failed to store uploaded document");
        }

        try {
            validatePdf(filePath);
        } catch (RuntimeException e) {
            deleteIfPresent(filePath);
            throw e;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        Files.deleteIfExists(filePath);
                    } catch (IOException cleanupException) {
                        log.warn("Failed to clean up uploaded file after transaction rollback: {}", filePath, cleanupException);
                    }
                }
            }
        });

        Document savedDocument = Document.builder()
                .originalFilename(originalFilename)
                .storedFilename(storedFileName)
                .contentType(fileContentType)
                .fileSize(file.getSize())
                .documentStatus(DocumentStatus.PROCESSING)
                .workspace(workspaceRepository.getReferenceById(workspaceId))
                .build();

        documentRepository.save(savedDocument);
        processingJobRepository.save(new DocumentProcessingJob(savedDocument.getId()));

        log.info("Document {} saved", savedDocument.getId());

        return new UploadDocumentResponse(savedDocument.getId(), savedDocument.getDocumentStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public UploadDocumentResponse getDocumentById(Long documentId, UUID workspaceId) {
        Document document = documentRepository.findVisibleByIdAndWorkspace(documentId, workspaceId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found with id: " + documentId));
        return new UploadDocumentResponse(document.getId(), document.getDocumentStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentSummaryResponse> listDocuments(UUID workspaceId) {
        return documentRepository.findVisibleToWorkspace(workspaceId).stream()
                .map(document -> new DocumentSummaryResponse(
                        document.getId(), document.getOriginalFilename(), document.getContentType(),
                        document.getFileSize(), document.getDocumentStatus(), document.getUploadedAt(),
                        document.isPublicDocument()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId, UUID workspaceId) {
        Document document = documentRepository.findByIdAndWorkspaceIdForUpdate(documentId, workspaceId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found with id: " + documentId));
        deleteLockedDocument(document);
    }

    @Override
    @Transactional
    public void deleteExpiredDocument(Long documentId) {
        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found with id: " + documentId));
        if (document.isRetentionExempt()) {
            return;
        }
        deleteLockedDocument(document);
    }

    private void deleteLockedDocument(Document document) {
        String storedFilename = document.getStoredFilename();

        fileCleanupRepository.save(new DocumentFileCleanupTask(storedFilename));
        documentRepository.delete(document);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    fileCleanupService.deletePendingFile(storedFilename);
                } catch (RuntimeException cleanupException) {
                    log.warn("Stored file cleanup deferred for {}", storedFilename, cleanupException);
                }
            }
        });
    }

    private void validatePdf(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            if (document.isEncrypted()) {
                throw new InvalidFile("Encrypted PDF files are not supported");
            }
        } catch (IOException e) {
            throw new InvalidFile("Uploaded file is not a valid PDF");
        }
    }

    private void deleteIfPresent(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException cleanupException) {
            log.warn("Failed to clean up rejected upload: {}", filePath, cleanupException);
        }
    }

}
