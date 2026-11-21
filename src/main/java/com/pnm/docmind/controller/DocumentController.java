package com.pnm.docmind.controller;

import com.pnm.docmind.dto.UploadDocumentResponse;
import com.pnm.docmind.dto.DocumentSummaryResponse;
import com.pnm.docmind.dto.ConversationMessageResponse;
import com.pnm.docmind.dto.ConversationSummaryResponse;
import com.pnm.docmind.service.document.DocumentService;
import com.pnm.docmind.service.questionAnswer.ConversationService;
import com.pnm.docmind.service.ratelimit.WorkspaceRateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final ConversationService conversationService;
    private final WorkspaceRateLimitService rateLimitService;

    @PostMapping
    public ResponseEntity<UploadDocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("workspaceId") UUID workspaceId) {

        rateLimitService.checkUploadLimit(workspaceId);
        UploadDocumentResponse response = documentService.upload(file, workspaceId);

        return ResponseEntity.accepted().body(response);

    }

    @GetMapping("/{id}")
    public ResponseEntity<UploadDocumentResponse> getDocumentStatus(
            @PathVariable Long id,
            @RequestAttribute("workspaceId") UUID workspaceId) {

        UploadDocumentResponse documentResponse = documentService.getDocumentById(id, workspaceId);

        return ResponseEntity.ok(documentResponse);
    }

    @GetMapping
    public ResponseEntity<List<DocumentSummaryResponse>> listDocuments(
            @RequestAttribute("workspaceId") UUID workspaceId) {
        return ResponseEntity.ok(documentService.listDocuments(workspaceId));
    }

    @GetMapping("/{id}/conversations")
    public ResponseEntity<List<ConversationSummaryResponse>> listConversations(
            @PathVariable Long id,
            @RequestAttribute("workspaceId") UUID workspaceId) {
        return ResponseEntity.ok(conversationService.listForDocument(id, workspaceId));
    }

    @GetMapping("/{id}/conversations/{conversationId}/messages")
    public ResponseEntity<List<ConversationMessageResponse>> listConversationMessages(
            @PathVariable Long id,
            @PathVariable Long conversationId,
            @RequestAttribute("workspaceId") UUID workspaceId) {
        return ResponseEntity.ok(conversationService.listMessages(id, conversationId, workspaceId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            @RequestAttribute("workspaceId") UUID workspaceId) {
        documentService.deleteDocument(id, workspaceId);
        return ResponseEntity.noContent().build();
    }

}
