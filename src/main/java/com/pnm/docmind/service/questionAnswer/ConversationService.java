package com.pnm.docmind.service.questionAnswer;

import com.pnm.docmind.constant.ChatMessageRole;
import com.pnm.docmind.entity.ChatMessage;
import com.pnm.docmind.entity.Conversation;
import com.pnm.docmind.entity.Document;
import com.pnm.docmind.exception.ConversationNotFoundException;
import com.pnm.docmind.exception.DocumentNotFoundException;
import com.pnm.docmind.dto.ConversationMessageResponse;
import com.pnm.docmind.dto.ConversationSummaryResponse;
import com.pnm.docmind.repository.ChatMessageRepository;
import com.pnm.docmind.repository.ConversationRepository;
import com.pnm.docmind.repository.DocumentRepository;
import com.pnm.docmind.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int MAX_HISTORY_MESSAGES = 10;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final WorkspaceRepository workspaceRepository;

    public Conversation findExisting(Long conversationId, Document document, UUID workspaceId) {
        if (conversationId == null) {
            return null;
        }

        return conversationRepository.findByIdAndDocument_IdAndWorkspace_Id(
                        conversationId, document.getId(), workspaceId)
                .orElseThrow(() -> new ConversationNotFoundException(
                        "Conversation not found for document id: " + document.getId()));
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getRecentHistory(Long conversationId) {
        List<ChatMessage> newestFirst = chatMessageRepository.findByConversation_IdOrderByIdDesc(
                conversationId,
                PageRequest.of(0, MAX_HISTORY_MESSAGES));
        List<ChatMessage> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return chronological;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> listForDocument(Long documentId, UUID workspaceId) {
        if (documentRepository.findVisibleByIdAndWorkspace(documentId, workspaceId).isEmpty()) {
            throw new DocumentNotFoundException("Document not found with id: " + documentId);
        }

        return conversationRepository.findByDocument_IdAndWorkspace_IdOrderByUpdatedAtDescIdDesc(
                        documentId, workspaceId)
                .stream()
                .map(conversation -> new ConversationSummaryResponse(
                        conversation.getId(), conversation.getCreatedAt(), conversation.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationMessageResponse> listMessages(Long documentId, Long conversationId, UUID workspaceId) {
        if (documentRepository.findVisibleByIdAndWorkspace(documentId, workspaceId).isEmpty()) {
            throw new DocumentNotFoundException("Document not found with id: " + documentId);
        }

        if (conversationRepository.findByIdAndDocument_IdAndWorkspace_Id(
                conversationId, documentId, workspaceId).isEmpty()) {
            throw new ConversationNotFoundException(
                    "Conversation not found for document id: " + documentId);
        }

        return chatMessageRepository.findByConversation_IdOrderByIdAsc(conversationId)
                .stream()
                .map(message -> new ConversationMessageResponse(
                        message.getId(), message.getRole(), message.getContent(), message.getCreatedAt()))
                .toList();
    }

    @Transactional
    public Long saveTurn(
            Conversation conversation,
            Document document,
            UUID workspaceId,
            String question,
            String answer) {
        Conversation activeConversation = conversation == null
                ? conversationRepository.save(Conversation.builder()
                        .document(document)
                        .workspace(workspaceRepository.getReferenceById(workspaceId))
                        .build())
                : conversation;
        chatMessageRepository.saveAll(List.of(
                ChatMessage.builder()
                        .conversation(activeConversation)
                        .role(ChatMessageRole.USER)
                        .content(question)
                        .build(),
                ChatMessage.builder()
                        .conversation(activeConversation)
                        .role(ChatMessageRole.ASSISTANT)
                        .content(answer)
                        .build()
        ));
        conversationRepository.updateLastMessageTime(activeConversation.getId());
        return activeConversation.getId();
    }
}
