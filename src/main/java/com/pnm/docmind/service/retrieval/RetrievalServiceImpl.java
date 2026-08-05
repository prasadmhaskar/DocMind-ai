package com.pnm.docmind.service.retrieval;

import com.pnm.docmind.constant.DocumentStatus;
import com.pnm.docmind.dto.RetrievedChunk;
import com.pnm.docmind.dto.RetrievedChunkResponse;
import com.pnm.docmind.entity.Document;
import com.pnm.docmind.exception.DocumentNotFoundException;
import com.pnm.docmind.exception.DocumentProcessingException;
import com.pnm.docmind.repository.DocumentChunkJdbcRepository;
import com.pnm.docmind.repository.DocumentRepository;
import com.pnm.docmind.service.embedding.EmbeddingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalServiceImpl implements RetrievalService {

    @Value("${rag.retrieval.maxResults}")
    private int maxResults;

    @Value("${rag.retrieval.minimum-similarity}")
    private double minimumSimilarity;

    private final EmbeddingService embeddingService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkJdbcRepository documentChunkJdbcRepository;

    @Override
    public List<RetrievedChunkResponse> retrieve(Long documentId, String question) {

        Document document = documentRepository.findById(documentId).orElseThrow(() ->
                new DocumentNotFoundException("Document not found with id: " + documentId));

        log.info("Retrieving chunks for document {}", documentId);

        switch (document.getDocumentStatus()) {
            case READY -> { }
            case UPLOADING, PROCESSING ->
                    throw new DocumentProcessingException("Document processing not completed yet, please wait.");
            case FAILED ->
                    throw new DocumentProcessingException("Document processing failed.");
        }

        float[] queryEmbedding = embeddingService.generateQueryEmbedding(question);

        log.info("Generated query embeddings");

        List<RetrievedChunk> retrievedChunks = documentChunkJdbcRepository.search(documentId, queryEmbedding, maxResults);

        log.info("Retrieved {} chunks", retrievedChunks.size());

        List<RetrievedChunkResponse> response = retrievedChunks.stream()
                .filter(chunk -> chunk.similarity() >= minimumSimilarity)
                .map(chunk -> new RetrievedChunkResponse(
                        chunk.chunkIndex(),
                        chunk.similarity(),
                        chunk.content()))
                .toList();

        log.info("{} chunks passed threshold {}", response.size(), minimumSimilarity);

        log.info("Retrieved {} relevant chunks", response.size());

        return response;

    }

    @PostConstruct
    void validateRetrievalProperties() {
        if (maxResults <= 0) {
            throw new IllegalArgumentException("rag.retrieval.top-k must be greater than 0");
        }

        if (minimumSimilarity < -1 || minimumSimilarity > 1) {
            throw new IllegalArgumentException("rag.retrieval.minimum-similarity must be between -1 and 1");
        }
    }
}
