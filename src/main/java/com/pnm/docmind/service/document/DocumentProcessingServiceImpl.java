package com.pnm.docmind.service.document;

import com.pnm.docmind.entity.Document;
import com.pnm.docmind.exception.DocumentNotFoundException;
import com.pnm.docmind.exception.DocumentProcessingException;
import com.pnm.docmind.exception.EmptyFile;
import com.pnm.docmind.repository.DocumentRepository;
import com.pnm.docmind.service.chunk.ChunkingService;
import com.pnm.docmind.service.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final PdfExtractionService pdfExtractionService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final PersistDocumentChunk persistDocumentChunk;
    private final DocumentStatusService documentStatusService;

    @Value("${app.storage.path}")
    private String storagePath;

    @Override
    @Async
    public void processDocumentAsync(Long documentId) {

        log.info("Started processing document {}", documentId);

        try {

            Document document = documentRepository.findById(documentId).orElseThrow(() -> new DocumentNotFoundException("Document not found with id: " + documentId));

            Path path = Paths.get(storagePath, document.getStoredFilename());

            log.info("Extracting text from {}", document.getStoredFilename());

            String extractedText = pdfExtractionService.extractText(path);

            log.info("Extracted {} characters", extractedText.length());

            if (extractedText.isBlank()) {
                throw new EmptyFile("No text found in uploaded file");
            }

            if (extractedText.strip().length() < 10) {
                throw new EmptyFile("Extracted text is too short.");
            }

            List<String> chunkedTextList = chunkingService.chunk(extractedText);

            if (chunkedTextList.isEmpty()) {
                throw new DocumentProcessingException("Chunked text list is empty.");
            }

            log.info("Generated {} chunks for documentId={}", chunkedTextList.size(), documentId);

            List<float[]> generatedEmbeddings = embeddingService.generateEmbedding(chunkedTextList);

            if (chunkedTextList.size() != generatedEmbeddings.size()) {
                throw new DocumentProcessingException("Chunk and embedding count mismatch");
            }

            persistDocumentChunk.persist(document, chunkedTextList, generatedEmbeddings);

        } catch (Exception e) {
            log.error("Document processing failed. documentId={}", documentId, e);

            try {
                documentStatusService.markFailed(documentId);
            } catch (Exception statusException) {
                log.error("Failed to mark document as FAILED. documentId={}", documentId, statusException);
            }
        }


    }
}
