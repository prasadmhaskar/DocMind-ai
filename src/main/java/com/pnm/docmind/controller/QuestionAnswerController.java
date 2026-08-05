package com.pnm.docmind.controller;

import com.pnm.docmind.dto.AskQuestionRequest;
import com.pnm.docmind.dto.AskQuestionResponse;
import com.pnm.docmind.dto.RetrievedChunkResponse;
import com.pnm.docmind.service.questionAnswer.QuestionAnswerService;
import com.pnm.docmind.service.retrieval.RetrievalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionAnswerController {

    private final QuestionAnswerService questionAnswerService;

    @PostMapping("/documents/{documentId}/ask")
    public ResponseEntity<AskQuestionResponse> askQuestion(@RequestBody @Valid AskQuestionRequest request,  @PathVariable Long documentId) {

        AskQuestionResponse response = questionAnswerService.retrieve(documentId, request.question());

        return ResponseEntity.ok(response);

    }
}
