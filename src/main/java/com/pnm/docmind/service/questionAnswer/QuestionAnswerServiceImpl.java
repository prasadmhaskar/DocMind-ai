package com.pnm.docmind.service.questionAnswer;

import com.pnm.docmind.component.PromptBuilder;
import com.pnm.docmind.constant.ChatMessageRole;
import com.pnm.docmind.dto.AskQuestionResponse;
import com.pnm.docmind.dto.QuestionStreamEvent;
import com.pnm.docmind.dto.RetrievedChunk;
import com.pnm.docmind.dto.SourceResponse;
import com.pnm.docmind.entity.ChatMessage;
import com.pnm.docmind.entity.Conversation;
import com.pnm.docmind.entity.Document;
import com.pnm.docmind.exception.DocumentNotFoundException;
import com.pnm.docmind.exception.DocumentProcessingException;
import com.pnm.docmind.repository.DocumentChunkJdbcRepository;
import com.pnm.docmind.repository.DocumentRepository;
import com.pnm.docmind.service.embedding.EmbeddingService;
import com.pnm.docmind.service.ai.ChatProviderRouter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionAnswerServiceImpl implements QuestionAnswerService {
    @Value("${rag.retrieval.maxResults}")
    private int maxResults;

    @Value("${rag.retrieval.list-max-results:12}")
    private int listMaxResults;

    @Value("${rag.retrieval.minimum-similarity}")
    private double minimumSimilarity;

    private final EmbeddingService embeddingService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkJdbcRepository documentChunkJdbcRepository;
    private final ChatProviderRouter chatProviderRouter;
    private final PromptBuilder promptBuilder;
    private final ConversationService conversationService;

    @Override
    public AskQuestionResponse retrieve(Long documentId, Long conversationId, String question, UUID workspaceId) {
        long start = System.currentTimeMillis();
        PreparedQuestion prepared = prepareQuestion(documentId, conversationId, question, workspaceId);
        String answer = prepared.topChunks().isEmpty()
                ? "I couldn't find relevant information in the document."
                : chatProviderRouter.call(prepared.prompt());

        if (answer == null || answer.isBlank()) {
            throw new DocumentProcessingException("AI model returned an empty answer.");
        }

        AskQuestionResponse response = persistResponse(prepared, answer);
        log.info("Answer generated from model for document {}", documentId);
        log.info("Question answered in {} ms", System.currentTimeMillis() - start);
        return response;
    }

    @Override
    public SseEmitter stream(Long documentId, Long conversationId, String question, UUID workspaceId) {
        requireAccessibleDocument(documentId, workspaceId);
        SseEmitter emitter = new SseEmitter(300_000L);
        Disposable.Composite subscriptions = Disposables.composite();
        emitter.onCompletion(subscriptions::dispose);
        emitter.onTimeout(() -> {
            subscriptions.dispose();
            emitter.complete();
        });

        emitter.onError(error -> subscriptions.dispose());
        Disposable preparation = Schedulers.boundedElastic().schedule(() -> {
            if (subscriptions.isDisposed()) {
                return;
            }

            if (!sendEvent(emitter, new QuestionStreamEvent(
                    "status", null, null, null, null, "Retrieving document context..."), subscriptions)) {
                return;
            }

            PreparedQuestion prepared;
            try {
                prepared = prepareQuestion(documentId, conversationId, question, workspaceId);
            } catch (RuntimeException preparationError) {
                log.error("Preparing streamed answer failed. documentId={}", documentId, preparationError);
                sendError(emitter, subscriptions);
                return;
            }

            if (subscriptions.isDisposed()) {
                return;
            }

            Flux<QuestionStreamEvent> events = createStreamEvents(prepared, () -> sendEvent(
                    emitter,
                    new QuestionStreamEvent(
                            "status", null, null, null, null,
                            "Primary AI provider is unavailable; retrying with the backup provider..."),
                    subscriptions));
            Disposable stream = events.subscribe(
                    data -> sendEvent(emitter, data, subscriptions),
                    streamError -> {
                        log.error("Streaming answer failed. documentId={}", documentId, streamError);
                        sendError(emitter, subscriptions);
                    },
                    emitter::complete);
            subscriptions.add(stream);
        });
        subscriptions.add(preparation);
        return emitter;
    }

    private Flux<QuestionStreamEvent> createStreamEvents(PreparedQuestion prepared, Runnable onFallback) {
        return Flux.defer(() -> {
            StringBuilder answer = new StringBuilder();
            Flux<String> generatedChunks = prepared.topChunks().isEmpty()
                    ? Flux.just("I couldn't find relevant information in the document.")
                    : chatProviderRouter.streamText(prepared.prompt(), onFallback);

            return Flux.concat(
                    Flux.just(new QuestionStreamEvent(
                            "start", null, null, null, prepared.sources(), null)),
                    generatedChunks
                            .publishOn(Schedulers.boundedElastic())
                            .filter(chunk -> chunk != null && !chunk.isEmpty())
                            .doOnNext(answer::append)
                            .map(chunk -> new QuestionStreamEvent(
                                    "delta", null, chunk, null, null, null)),
                    Mono.fromSupplier(() -> {
                        String completeAnswer = answer.toString();
                        if (completeAnswer.isBlank()) {
                            throw new DocumentProcessingException("AI model returned an empty answer.");
                        }
                        AskQuestionResponse response = persistResponse(prepared, completeAnswer);
                        return new QuestionStreamEvent(
                                "complete",
                                response.conversationId(),
                                null,
                                response.answer(),
                                response.sources(),
                                null);
                    }));
        });
    }

    private boolean sendEvent(
            SseEmitter emitter,
            QuestionStreamEvent data,
            Disposable.Composite subscriptions) {
        if (subscriptions.isDisposed()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(data.type()).data(data));
            return true;
        } catch (IOException sendError) {
            subscriptions.dispose();
            emitter.completeWithError(sendError);
            return false;
        }
    }

    private void sendError(SseEmitter emitter, Disposable.Composite subscriptions) {
        if (sendEvent(emitter, new QuestionStreamEvent(
                "error", null, null, null, null,
                "The answer could not be completed. Please retry."), subscriptions)) {
            emitter.complete();
        }
    }

    private PreparedQuestion prepareQuestion(Long documentId, Long conversationId, String question, UUID workspaceId) {
        Document document = documentRepository.findVisibleByIdAndWorkspace(documentId, workspaceId).orElseThrow(() ->
                new DocumentNotFoundException("Document not found with id: " + documentId));

        log.info("Retrieving chunks for document {}", documentId);
        switch (document.getDocumentStatus()) {
            case READY -> { }
            case UPLOADING, PROCESSING ->
                    throw new DocumentProcessingException("Document processing not completed yet, please wait.");
            case FAILED -> throw new DocumentProcessingException("Document processing failed.");
        }

        String trimmedQuestion = question.trim();
        Conversation conversation = conversationService.findExisting(conversationId, document, workspaceId);
        List<ChatMessage> history = conversation == null
                ? List.of()
                : conversationService.getRecentHistory(conversation.getId());
        boolean followUp = refersToConversation(trimmedQuestion);
        List<ChatMessage> promptHistory = followUp ? history : List.of();
        String retrievalQuery = buildRetrievalQuery(trimmedQuestion, history, followUp);
        int resultLimit = isListQuestion(trimmedQuestion) ? listMaxResults : maxResults;
        float[] queryEmbedding = embeddingService.generateQueryEmbedding(retrievalQuery);
        List<RetrievedChunk> topChunks = documentChunkJdbcRepository.search(
                documentId, queryEmbedding, retrievalQuery, resultLimit, minimumSimilarity);
        List<SourceResponse> sources = topChunks.stream()
                .map(chunk -> new SourceResponse(chunk.pageNumber(), chunk.chunkIndex(), chunk.similarity()))
                .toList();
        String prompt = topChunks.isEmpty()
                ? null
                : promptBuilder.build(trimmedQuestion, promptHistory, topChunks);

        log.info("Retrieved {} relevant chunks for document {}", topChunks.size(), documentId);
        return new PreparedQuestion(document, conversation, workspaceId, trimmedQuestion, topChunks, sources, prompt);
    }

    private void requireAccessibleDocument(Long documentId, UUID workspaceId) {
        if (documentRepository.findVisibleByIdAndWorkspace(documentId, workspaceId).isEmpty()) {
            throw new DocumentNotFoundException("Document not found with id: " + documentId);
        }
    }

    private String buildRetrievalQuery(String question, List<ChatMessage> history, boolean followUp) {
        if (!followUp) {
            return question;
        }

        String previousUserQuestion = history.stream()
                .filter(message -> message.getRole() == ChatMessageRole.USER)
                .map(ChatMessage::getContent)
                .reduce((first, second) -> second)
                .orElse("");
        return previousUserQuestion.isBlank() ? question : previousUserQuestion + "\n" + question;
    }

    private boolean isListQuestion(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        boolean asksForMultipleItems = normalized.matches("(?s).*\\b(all|every|each|list|enumerate|seven|7|five|5|three|3)\\b.*");
        boolean namesACollection = normalized.matches("(?s).*\\b(layers?|steps?|types?|stages?|factors?|features?|examples?|reasons?|ways?|components?|methods?|functions?|advantages?|disadvantages?)\\b.*");
        boolean asksForNames = normalized.matches("(?s).*\\b(names? of|name all)\\b.*");
        boolean asksForModelOverview = normalized.matches("(?s).*\\bosi\\b.*")
                && normalized.matches("(?s).*\\b(explain|describe|overview|model)\\b.*");
        return ((asksForMultipleItems || asksForNames) && namesACollection) || asksForModelOverview;
    }

    private boolean refersToConversation(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.matches("(?s).*\\b(it|this|that|these|those|they|them|their|its|former|latter|above|same)\\b.*")
                || normalized.matches("(?s)^\\s*and\\b.*");
    }

    private AskQuestionResponse persistResponse(PreparedQuestion prepared, String answer) {
        Long conversationId = conversationService.saveTurn(
                prepared.conversation(), prepared.document(), prepared.workspaceId(), prepared.question(), answer);
        return new AskQuestionResponse(conversationId, answer, prepared.sources());
    }

    private record PreparedQuestion(
            Document document,
            Conversation conversation,
            UUID workspaceId,
            String question,
            List<RetrievedChunk> topChunks,
            List<SourceResponse> sources,
            String prompt) { }

    @PostConstruct
    void validateRetrievalProperties() {
        if (maxResults <= 0 || listMaxResults < maxResults) {
            throw new IllegalArgumentException("rag.retrieval result limits must be positive and list-max-results must be at least maxResults");
        }

        if (minimumSimilarity < -1 || minimumSimilarity > 1) {
            throw new IllegalArgumentException("rag.retrieval.minimum-similarity must be between -1 and 1");
        }
    }
}
