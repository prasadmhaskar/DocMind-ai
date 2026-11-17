package com.pnm.docmind.service.questionAnswer;

import com.pnm.docmind.dto.AskQuestionResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

public interface QuestionAnswerService {
    AskQuestionResponse retrieve(Long documentId, Long conversationId, String question, UUID workspaceId);
    SseEmitter stream(Long documentId, Long conversationId, String question, UUID workspaceId);
}
