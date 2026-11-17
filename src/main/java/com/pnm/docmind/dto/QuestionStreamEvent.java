package com.pnm.docmind.dto;

import java.util.List;

public record QuestionStreamEvent(
        String type,
        Long conversationId,
        String delta,
        String answer,
        List<SourceResponse> sources,
        String message
) { }
