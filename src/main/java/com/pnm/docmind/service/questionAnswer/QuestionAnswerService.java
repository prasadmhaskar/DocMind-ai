package com.pnm.docmind.service.questionAnswer;

import com.pnm.docmind.dto.AskQuestionResponse;

public interface QuestionAnswerService {
    AskQuestionResponse retrieve(Long documentId, String question);
}
