package com.pnm.docmind.service.document;

import com.pnm.docmind.dto.DocumentChunkData;
import com.pnm.docmind.dto.PageContent;
import com.pnm.docmind.entity.Document;
import com.pnm.docmind.exception.DocumentNotFoundException;
import com.pnm.docmind.exception.DocumentProcessingException;
import com.pnm.docmind.exception.EmptyFile;
import com.pnm.docmind.repository.DocumentRepository;
import com.pnm.docmind.service.chunk.ChunkingService;
import com.pnm.docmind.service.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final PersistDocumentChunk persistDocumentChunk;
    private final TextExtractionOrchestrator textExtractionOrchestrator;
    private final DocumentProcessingJobService processingJobService;

    @Value("${app.storage.path}")
    private String storagePath;


    @Override
    @Async("documentProcessingExecutor")
    public void processDocumentAsync(Long documentId) {

        log.info("Started processing document {}", documentId);
        processingJobService.markActive(documentId);
        try {
            Document document = documentRepository.findById(documentId).orElseThrow(() ->
                    new DocumentNotFoundException("Document not found with id: " + documentId));

            Path path = Paths.get(storagePath, document.getStoredFilename());

            log.info("Extracting text from {}", document.getStoredFilename());

            List<PageContent> extractedPageContent = textExtractionOrchestrator.extract(path);

            log.info("Extracted {} pages", extractedPageContent.size());

            if (extractedPageContent.isEmpty()) {
                throw new EmptyFile("No text found in uploaded file");
            }

            if (extractedPageContent.size() == 1 && extractedPageContent.getFirst().text().strip().length() < 10) {
                throw new EmptyFile("Extracted text is too short.");
            }

            List<DocumentChunkData> documentChunks = new ArrayList<>();

            int chunkIndex = 0;

            for (PageContent extractedTextPage : extractedPageContent) {
                List<DocumentChunkData> chunk = chunkingService.chunk(extractedTextPage, chunkIndex);
                if (chunk.isEmpty()) {
                    continue;
                }
                documentChunks.addAll(chunk);
                chunkIndex = chunk.getLast().chunkIndex() + 1;
            }

            if (documentChunks.isEmpty()) {
                throw new DocumentProcessingException("Chunked text list is empty.");
            }

            log.info("Generated {} chunks for documentId={}", documentChunks.size(), documentId);

            List<String> chunks = documentChunks.stream().map(DocumentChunkData::content).toList();

            List<float[]> generatedEmbeddings = embeddingService.generateDocumentEmbedding(chunks);

            if (chunks.size() != generatedEmbeddings.size()) {
                throw new DocumentProcessingException("Chunk and embedding count mismatch");
            }

            persistDocumentChunk.persist(document, documentChunks, generatedEmbeddings);

        } catch (Exception e) {
            log.error("Document processing failed. documentId={}", documentId, e);
            try {
                processingJobService.recordFailure(documentId, e);
            } catch (Exception recoveryException) {
                // The processing job lease remains in the database and can be reclaimed after it expires.
                log.error("Failed to record processing failure; job will be recovered after its lease expires. documentId={}",
                        documentId, recoveryException);
            }
        } finally {
            processingJobService.markInactive(documentId);
        }


    }
}
