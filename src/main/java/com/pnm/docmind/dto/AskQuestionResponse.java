package com.pnm.docmind.dto;

import java.util.List;

public record AskQuestionResponse(Long conversationId, String answer, List<SourceResponse> sources) { }
