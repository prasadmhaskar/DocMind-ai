package com.pnm.docmind.service.document;

import com.pnm.docmind.constant.DocumentStatus;
import com.pnm.docmind.dto.UploadDocumentResponse;
import com.pnm.docmind.entity.Document;
import com.pnm.docmind.exception.EmptyFile;
import com.pnm.docmind.exception.InvalidFile;
import com.pnm.docmind.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentProcessingService documentProcessingService;

    @Value("${app.storage.path}")
    private String storagePath;

    @Override
    @Transactional
    public UploadDocumentResponse upload(MultipartFile file) {

        if (file.isEmpty() || file.getSize() == 0) {
            throw new EmptyFile("File is empty");
        }

        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));

        if(originalFilename.contains("..") || originalFilename.isBlank()){
            throw new InvalidFile("Invalid file name or file type");
        }

        String fileContentType = file.getContentType();

        if(fileContentType == null || !Objects.equals(fileContentType, "application/pdf")){
            throw new InvalidFile("File type is not supported. Please upload a PDF file");
        }

        log.info("Uploading file {}", originalFilename);

        String storedFileName = UUID.randomUUID() + ".pdf";

        try {
            Path uploadPath = Paths.get(storagePath);

            Files.createDirectories(uploadPath);

            Path filePath = uploadPath.resolve(storedFileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Document savedDocument = Document.builder()
                .originalFilename(originalFilename)
                .storedFilename(storedFileName)
                .contentType(fileContentType)
                .fileSize(file.getSize())
                .documentStatus(DocumentStatus.PROCESSING)
                .build();

        documentRepository.save(savedDocument);

        log.info("Document {} saved", savedDocument.getId());

        documentProcessingService.processDocumentAsync(savedDocument.getId());

        return new UploadDocumentResponse(savedDocument.getId(), savedDocument.getDocumentStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public UploadDocumentResponse getDocumentById(String documentId) {

        return documentRepository.findByDocumentId(documentId);
    }

}
