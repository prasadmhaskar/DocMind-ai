package com.pnm.docmind.service.questionAnswer;

import com.pnm.docmind.component.PromptBuilder;
import com.pnm.docmind.dto.AskQuestionResponse;
import com.pnm.docmind.dto.RetrievedChunk;
import com.pnm.docmind.dto.RetrievedChunkResponse;
import com.pnm.docmind.dto.SourceResponse;
import com.pnm.docmind.entity.Document;
import com.pnm.docmind.exception.DocumentNotFoundException;
import com.pnm.docmind.exception.DocumentProcessingException;
import com.pnm.docmind.exception.QuestionNotValidException;
import com.pnm.docmind.repository.DocumentChunkJdbcRepository;
import com.pnm.docmind.repository.DocumentRepository;
import com.pnm.docmind.service.embedding.EmbeddingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionAnswerServiceImpl implements QuestionAnswerService {
    @Value("${rag.retrieval.maxResults}")
    private int maxResults;

    @Value("${rag.retrieval.minimum-similarity}")
    private double minimumSimilarity;

    private final EmbeddingService embeddingService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkJdbcRepository documentChunkJdbcRepository;
    private final ChatModel chatModel;
    private final PromptBuilder promptBuilder;

    @Override
    public AskQuestionResponse retrieve(Long documentId, String question) {

        long start = System.currentTimeMillis();

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

        log.info("Generated query embedding for documentId={}", documentId);

        List<RetrievedChunk> retrievedChunks = documentChunkJdbcRepository.search(documentId, queryEmbedding, maxResults);

        log.info("Retrieved {} chunks", retrievedChunks.size());

        List<RetrievedChunk> topChunks = retrievedChunks.stream()
                .filter(chunk -> chunk.similarity() >= minimumSimilarity)
                .toList();

        if(topChunks.isEmpty()){
            return new AskQuestionResponse(
                    "I couldn't find relevant information in the document.", List.of());
        }

        log.info("{} chunks passed threshold {}", topChunks.size(), minimumSimilarity);

        String prompt = promptBuilder.build(question, topChunks);

        String modelResponse = chatModel.call(prompt);

        AskQuestionResponse response = new AskQuestionResponse(
                modelResponse,
                topChunks.stream().map(
                        chunk -> new SourceResponse(chunk.chunkIndex(), chunk.similarity())
                ).toList()
        );

        log.info("Answer generated from model for document {}", documentId);

        log.info("Question answered in {} ms", System.currentTimeMillis() - start);

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
