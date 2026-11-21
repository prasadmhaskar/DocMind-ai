package com.pnm.docmind.controller;

import com.pnm.docmind.dto.AskQuestionRequest;
import com.pnm.docmind.dto.AskQuestionResponse;
import com.pnm.docmind.dto.QuestionStreamEvent;
import com.pnm.docmind.service.questionAnswer.QuestionAnswerService;
import com.pnm.docmind.service.ratelimit.WorkspaceRateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionAnswerController {

    private final QuestionAnswerService questionAnswerService;
    private final WorkspaceRateLimitService rateLimitService;

    @PostMapping("/documents/{documentId}/ask")
    public ResponseEntity<AskQuestionResponse> askQuestion(
            @RequestBody @Valid AskQuestionRequest request,
            @PathVariable Long documentId,
            @RequestAttribute("workspaceId") UUID workspaceId) {

        rateLimitService.checkQuestionLimit(workspaceId);
        AskQuestionResponse response = questionAnswerService.retrieve(
                documentId,
                request.conversationId(),
                request.question(),
                workspaceId);

        return ResponseEntity.ok(response);

    }

    @PostMapping(value = "/documents/{documentId}/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askQuestionStream(
            @RequestBody @Valid AskQuestionRequest request,
            @PathVariable Long documentId,
            @RequestAttribute("workspaceId") UUID workspaceId) {
        rateLimitService.checkQuestionLimit(workspaceId);
        return questionAnswerService.stream(documentId, request.conversationId(), request.question(), workspaceId);
    }
}
