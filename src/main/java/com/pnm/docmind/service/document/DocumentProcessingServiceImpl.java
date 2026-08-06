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
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
    private final DeleteFailedDocument deleteFailedDocument;

    @Value("${app.storage.path}")
    private String storagePath;


    @Override
    @Async
    public void processDocumentAsync(Long documentId) {

        log.info("Started processing document {}", documentId);

        Document document = documentRepository.findById(documentId).orElseThrow(() -> new DocumentNotFoundException("Document not found with id: " + documentId));

        try {

            Path path = Paths.get(storagePath, document.getStoredFilename());

            log.info("Extracting text from {}", document.getStoredFilename());

            List<PageContent> extractedTextPages = pdfExtractionService.extractText(path);

            log.info("Extracted {} pages", extractedTextPages.size());

            if (extractedTextPages.isEmpty()) {
                throw new EmptyFile("No text found in uploaded file");
            }

            if (extractedTextPages.size() == 1 && extractedTextPages.getFirst().text().strip().length() < 10) {
                throw new EmptyFile("Extracted text is too short.");
            }

            List<DocumentChunkData> documentChunks = new ArrayList<>();

            int chunkIndex = 0;

            for (PageContent extractedTextPage : extractedTextPages) {
                List<DocumentChunkData> chunk = chunkingService.chunk(extractedTextPage, chunkIndex);
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

            deleteFailedDocument.deleteDocument(document.getStoredFilename());

            try {
                documentStatusService.markFailed(documentId);
            } catch (Exception statusException) {
                log.error("Failed to mark document as FAILED. documentId={}", documentId, statusException);
            }
        }


    }
}
